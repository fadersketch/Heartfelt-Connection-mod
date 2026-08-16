package com.heartfelt.connection.memory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 事件历史系统(v1.3.0,P1)——让文本回应玩家的过去(galgame 化核心)。
 *
 * heartfelt 自己记录关键事件(写女仆 ForgeData NBT),注入 LLM prompt:
 * - 首次见面(heartfelt 首次观测到女仆属于该玩家)
 * - 首次送礼(首次喂食蛋糕,TLM 驯服物)
 * - 告白发起方 / 完成时刻(MaidConfessionManager 已写 CONFESSION_BY/AT)
 * - 救主(女仆击杀正攻击主人的敌对生物)
 * - 破裂史(累计次数)
 *
 * 全部走软读取:未装 maidmarriage/callresponse 时事件不触发,自动静默。
 * 注入入口:buildHistoryText(maid) → SmartPromptAppender 追加"我们一起经历的事"段。
 */
public final class EventHistoryManager {
    /** 首见/救主扫描间隔(tick,默认 200=10s) */
    private static final int SCAN_INTERVAL = 200;
    private int tick = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        if (++this.tick < SCAN_INTERVAL) {
            return;
        }
        this.tick = 0;
        long now = server.m_129921_();
        for (ServerLevel level : server.m_129785_()) {
            for (ServerPlayer player : level.m_6907_()) {
                for (EntityMaid maid : level.m_6443_(EntityMaid.class,
                        player.m_20191_().m_82400_(128.0),
                        e -> e.m_6084_() && e.m_21824_() && DialogueDispatcher.isOwner(e, player))) {
                    // 首次见面:首次观测到属于该玩家
                    if (maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET) <= 0L) {
                        maid.getPersistentData().m_128356_(HeartfeltTags.EVENT_FIRST_MEET, now);
                    }
                }
            }
        }
    }

    /** 首次送礼:玩家第一次给女仆喂蛋糕(与背叛悔改同判定,正常女仆也记录) */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!DialogueDispatcher.isOwner(maid, player)) {
            return;
        }
        if (!event.getItemStack().m_150930_(Items.f_42446_)) {
            return;
        }
        // v1.5.98:情感引擎喂脉冲——喂蛋糕 = 正向亲和(首次与后续都喂)
        com.heartfelt.connection.affect.AffectStateManager.onGift(maid);
        if (maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_GIFT) <= 0L) {
            maid.getPersistentData().m_128356_(HeartfeltTags.EVENT_FIRST_GIFT,
                    maid.m_9236_().m_46467_());
        }
    }

    /** 救主:女仆击杀正攻击主人的敌对生物(击杀者=主人的女仆,死者目标=主人) */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().m_7640_() instanceof EntityMaid maid)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LivingEntity owner = maid.m_269323_();
        if (!(owner instanceof ServerPlayer player) || !DialogueDispatcher.isOwner(maid, player)) {
            return;
        }
        // 死者最后攻击的是主人(m_21188_=getLastHurtByMob)——女仆替主人挡下了威胁
        if (victim.m_21188_() != owner) {
            return;
        }
        long now = maid.m_9236_().m_46467_();
        int count = maid.getPersistentData().m_128451_(HeartfeltTags.EVENT_SAVED_MASTER);
        maid.getPersistentData().m_128405_(HeartfeltTags.EVENT_SAVED_MASTER, count + 1);
        maid.getPersistentData().m_128356_(HeartfeltTags.EVENT_SAVED_AT, now);
    }

    // ==================== 注入:事件历史文本 ====================

    /** 生成"我们一起经历的事"段(供 SmartPromptAppender 注入);无事件返回空串 */
    public static String buildHistoryText(EntityMaid maid) {
        long now = maid.m_9236_().m_46467_();
        StringBuilder sb = new StringBuilder();
        appendDay(sb, maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET), now,
                "我们初次见面");
        appendDay(sb, maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_GIFT), now,
                "主人第一次喂我吃蛋糕");
        // 告白:由谁发起 + 哪天(仅已确认关系)
        if (RelationshipExemption.isConfessed(maid) || RelationshipExemption.isMarried(maid)) {
            long at = maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT);
            String by = maid.getPersistentData().m_128461_(HeartfeltTags.CONFESSION_BY);
            if (at > 0L) {
                String day = "第 " + (at / 24000L) + " 天";
                String initiator = "maid".equals(by) ? "是{maid}先向主人告白的" : "是主人先向{maid}告白的";
                sb.append(day).append(",我们告白了——").append(initiator).append("。 ");
            }
        }
        int saved = maid.getPersistentData().m_128451_(HeartfeltTags.EVENT_SAVED_MASTER);
        if (saved > 0) {
            sb.append("我曾在危险中守护过主人 ").append(saved).append(" 次。 ");
        }
        // v1.3.1:特殊奶(记录的是玩家侧的 NBT;借主人找——由 SmartPromptAppender 在
        // 女仆 prompt 里展示"主人喝过我为他准备的奶")
        LivingEntity owner = maid.m_269323_();
        if (owner != null) {
            long milkAt = owner.getPersistentData().m_128454_(HeartfeltTags.SPECIAL_MILK_AT);
            int milkCount = owner.getPersistentData().m_128451_(HeartfeltTags.SPECIAL_MILK_COUNT);
            if (milkCount > 0) {
                sb.append("主人喝过我为他准备的奶 ").append(milkCount).append(" 次");
                if (milkAt > 0L) {
                    sb.append("(最近一次是 ").append(daysAgoText(milkAt, now)).append(")");
                }
                sb.append("。 ");
            }
        }
        int breakups = maid.getPersistentData().m_128451_(HeartfeltTags.EVENT_BREAKUP_COUNT);
        if (breakups > 0) {
            sb.append("我们曾经有过 ").append(breakups).append(" 次关系波折。 ");
        }
        return sb.toString();
    }

    /** 破裂计数(供 MaidConfessionManager 破裂时调用) */
    public static void recordBreakup(EntityMaid maid) {
        int count = maid.getPersistentData().m_128451_(HeartfeltTags.EVENT_BREAKUP_COUNT);
        maid.getPersistentData().m_128405_(HeartfeltTags.EVENT_BREAKUP_COUNT, count + 1);
    }

    private static void appendDay(StringBuilder sb, long at, long now, String text) {
        if (at <= 0L) {
            return;
        }
        long daysAgo = Math.max(0L, (now - at) / 24000L);
        String when = daysAgo <= 0L ? "今天" : (daysAgo == 1L ? "昨天" : daysAgo + " 天前");
        sb.append(when).append(",").append(text).append("。 ");
    }

    /** "今天/昨天/N 天前"(at<=0 返回空串) */
    private static String daysAgoText(long at, long now) {
        if (at <= 0L) {
            return "";
        }
        long daysAgo = Math.max(0L, (now - at) / 24000L);
        if (daysAgo <= 0L) {
            return "今天";
        }
        return daysAgo == 1L ? "昨天" : daysAgo + " 天前";
    }
}
