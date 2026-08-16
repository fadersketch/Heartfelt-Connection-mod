package com.heartfelt.connection.combat;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.quota.ApiQuotaBridge;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 背叛悔改(和解)系统(v1.1.0 新增)。
 *
 * 背景:爱憎分明(callresponse)的背叛一旦触发就是永久敌对——triggerBetrayal 会
 * setOwnerUUID(null) 解除认主、写入 NBT IsBetraying=true,且没有任何释放条件;
 * 原版唯一旁路(狩猎令触发 resetBetrayal)既不可发现,又不恢复信任/恐惧数值,
 * 重新认主后 200 tick 内立刻再次背叛。自然恢复(每 10 分钟 +1 信任/-1 恐惧)要求
 * owner 是玩家,而背叛时 owner 已被清空 → 恢复完全失效。
 *
 * 本管理器提供一条"有戏剧性、可预期"的和解路径:
 *   主人(或未来的主人)对背叛女仆持续喂蛋糕安抚 → 每次降低恐惧、提升信任、
 *   累计安抚进度 → 恐惧 ≤90 且信任 ≥10 且安抚 ≥N 次 → 触发悔改:
 *   调用原版遗留公开 API resetBetrayal + 重新认主 + LLM 悔改对话 + 心情低落(愧疚)。
 *
 * 数值与阈值全部走 HeartfeltConfig;安抚中断超过配置天数进度清零。
 */
public final class BetrayalRedemptionManager {
    /** 悔改判定的信任达标线(与背叛触发线一致) */
    private static final int TRUST_DONE = 10;
    /** 悔改判定的恐惧达标线(与背叛触发线一致) */
    private static final int FEAR_DONE = 90;

    /**
     * 蛋糕(v1.5.1 修复:按注册名取,不依赖 SRG 字段名——原 Items.f_42446_ 实为
     * MILK_BUCKET 奶桶,导致拿蛋糕喂女仆永远无效)。
     * 审计 H-12：改为惰性解析，避免类加载早于注册表填充导致永久 null。
     */
    private static Item CAKE = null;
    private static boolean CAKE_RESOLVED = false;

    private static Item cake() {
        if (!CAKE_RESOLVED) {
            CAKE = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "cake"));
            CAKE_RESOLVED = true;
        }
        return CAKE;
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // 只服务端处理(PlayerEvent.getEntity() 返回 Player)
        }
        if (event.isCanceled() || !CallResponseCompat.isBetraying(maid)) {
            return;
        }
        // 只认蛋糕(TLM 驯服物,与背叛"重新驯服"的语义一致);其他物品不累计防刷
        Item cake = cake();
        if (cake == null || !event.getItemStack().m_150930_(cake)) {
            return;
        }
        onFeed(player, maid);
    }

    /** 每次喂食:降恐惧、升信任、累计进度;达标则触发悔改 */
    private static void onFeed(ServerPlayer player, EntityMaid maid) {
        long now = maid.m_9236_().m_46467_();
        long betrayedAt = maid.getPersistentData().m_128454_(HeartfeltTags.REDEMPTION_BETRAYED_AT);
        if (betrayedAt <= 0L) {
            betrayedAt = now;
            maid.getPersistentData().m_128356_(HeartfeltTags.REDEMPTION_BETRAYED_AT, now);
        }
        int maxFeeds = HeartfeltConfig.REDEMPTION_FEEDS.get();
        int progress = maid.getPersistentData().m_128451_(HeartfeltTags.REDEMPTION_PROGRESS);
        // 超过配置天数没有安抚,进度归零重新计数
        long staleTicks = HeartfeltConfig.REDEMPTION_STALE_DAYS.get() * 24000L;
        if (now - betrayedAt > staleTicks) {
            progress = 0;
        }
        progress = Math.min(progress + 1, maxFeeds);
        maid.getPersistentData().m_128405_(HeartfeltTags.REDEMPTION_PROGRESS, progress);
        // 记录首个安抚者(即未来重新认主的对象)
        if (!maid.getPersistentData().m_128403_(HeartfeltTags.REDEMPTION_REDEMPTOR)) {
            maid.getPersistentData().m_128362_(HeartfeltTags.REDEMPTION_REDEMPTOR, player.m_20148_());
        }

        UUID playerId = player.m_20148_();
        // 恐惧 -N / 信任 +M(直接写爱憎分明的情绪数据)
        CallResponseCompat.addFearFloat(maid, playerId,
                -HeartfeltConfig.REDEMPTION_FEAR_DROP.get().floatValue());
        CallResponseCompat.addTrustFloat(maid, playerId,
                HeartfeltConfig.REDEMPTION_TRUST_GAIN.get().floatValue());

        int[] emotion = CallResponseCompat.emotionValues(maid, playerId);
        int fear = emotion == null ? FEAR_DONE + 1 : emotion[1];
        int trust = emotion == null ? TRUST_DONE - 1 : emotion[0];
        if ((maxFeeds <= 0 || progress >= maxFeeds) && fear <= FEAR_DONE && trust >= TRUST_DONE) {
            completeRedemption(player, maid);
        } else {
            // 进度反馈:喂食时冒一段"还在抗拒/开始动摇"的气泡
            maid.getChatBubbleManager().addTextChatBubble(
                    PromptTexts.redemptionBubble(progress, maxFeeds));
        }
    }

    /** 悔改完成:解除背叛状态 → 重新认主 → LLM 悔改对话 → 心情"愧疚" → 清仇恨标记 */
    private static void completeRedemption(ServerPlayer player, EntityMaid maid) {
        CallResponseCompat.resetBetrayal(maid);
        // 重新认主(与背叛时 setOwnerUUID(null) 对称;m_7105_=setTame)
        try {
            maid.m_21816_(player.m_20148_());
            maid.m_7105_(true);
            maid.m_21837_(false);
        } catch (Exception ignored) {
        }
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_PROGRESS);
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_BETRAYED_AT);
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_REDEMPTOR);
        maid.getPersistentData().m_128379_(HeartfeltTags.REDEMPTED, true);
        maid.getPersistentData().m_128356_(HeartfeltTags.REDEMPTED_AT, maid.m_9236_().m_46467_());
        // A2:悔改即和好,清除仇恨标记(含时间戳)
        maid.getPersistentData().m_128473_(HeartfeltTags.HATED_PLAYER);
        maid.getPersistentData().m_128473_(HeartfeltTags.HATED_AT);

        // 心情联动(maidmarriage):悔改后当天心情低落(愧疚),之后由互动自然回升
        MaidMarriageCompat.addMood(maid, -5);

        // LLM 悔改对话(纳入全局 API 配额)
        if (ApiQuotaBridge.tryAcquire()) {
            try {
                maid.getAiChatManager().chat(PromptTexts.REDEMPTION_DIALOGUE,
                        ChatClientInfo.fromMaid(maid), player);
            } catch (Exception ignored) {
            }
        }
        player.m_213846_(Component.m_237113_(PromptTexts.redemptionDone(maid.m_7755_().getString())));
    }

    /**
     * 调试用:跳过安抚进度直接悔改(HeartfeltDebugApi.forceRedemption 的落地实现,
     * 由手册"强制悔改"按钮调用;服务端执行)。
     */
    public static void completeRedemptionForDebug(EntityMaid maid, Player player) {
        if (maid == null || player == null) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            completeRedemption(serverPlayer, maid);
        } else {
            // 非服务端兜底:至少解除背叛状态,不重认主
            CallResponseCompat.resetBetrayal(maid);
        }
    }
}
