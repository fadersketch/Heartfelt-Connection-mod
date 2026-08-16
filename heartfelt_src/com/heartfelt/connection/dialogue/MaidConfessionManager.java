package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.network.HeartfeltNetwork;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 女仆主动告白 + 关系破裂(v1.2.0,用户确认的关系模型;v1.5.0 前摇重构)。
 *
 * 关系模型:
 * - 好感 &gt; 192(恋爱线):女仆意识到自己喜欢主人 → 周期性尝试【主动告白】
 *   概率随好感升高(192→384 线性加成);好感越高越主动
 * - 主人【已宣布关系】(已婚/有恋人)时:不再发起尝试(已宣布则不尝试)
 * - 主动告白触发前做【威胁检查】:以女仆为中心大半径内无敌对生物、女仆不在战斗中,
 *   且主人距离在触发范围内 → 启动【前摇】(v1.5.0):
 *   系统消息提示 → 女仆走向玩家 → 走到身边 → ConfessionApproachManager
 *   发 S2C 跳转 maidmarriage 的告白选项界面(世界可见,非全屏)
 * - 接受/拒绝由 maidmarriage 剧本处理(heartfelt 补记记忆/时间戳)
 * - 【关系破裂】:恋人好感跌破告白线(128)→ 重置告白状态 + 心碎记忆 + 心情惩罚;
 *   冻结解除(信任/恐惧恢复移动),其他暗恋者重新有机会
 */
public class MaidConfessionManager {
    /** 破裂监视间隔(tick,默认 200=10s,让"跌破线→破裂"及时) */
    private static final int BREAKUP_INTERVAL = 200;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long tick = server.m_129921_();
        if (tick % BREAKUP_INTERVAL == 0) {
            this.checkBreakups(server);
        }
        int interval = HeartfeltConfig.CONFESSION_ATTEMPT_INTERVAL.get();
        if (interval > 0 && tick % interval == 0) {
            this.attemptConfessions(server);
        }
    }

    // ==================== 破裂监视 ====================

    /** 恋人好感跌破告白线 → 关系破裂(重置告白 + 心碎记忆 + 心情惩罚) */
    private void checkBreakups(MinecraftServer server) {
        long now = server.m_129921_();
        int line = HeartfeltConfig.FREEZE_CONFESSION_LINE.get();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            // v1.5.18:周期补标记——任一女仆确认关系则写主人全局标记(绝对压制吃醋,
            // 覆盖 maidmarriage 自己 UI 的确认路径)
            RelationshipExemption.sweepDedicated(player);
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                if (!RelationshipExemption.isConfessed(maid) || RelationshipExemption.isMarried(maid)) {
                    continue; // 只处理恋人(已婚是永久契约,v1.2.0 不离婚)
                }
                if (maid.getFavorability() >= line) {
                    continue;
                }
                // 破裂
                MaidMarriageCompat.resetConfession(maid);
                maid.getPersistentData().m_128356_(HeartfeltTags.HEARTBROKEN_AT, now);
                // v1.5.98:情感引擎喂脉冲——关系破裂 → 冲突/受伤债大幅上升
                com.heartfelt.connection.affect.AffectStateManager.onHeartbroken(maid);
                // v1.3.0:破裂史计数(事件历史 P1)
                com.heartfelt.connection.memory.EventHistoryManager.recordBreakup(maid);
                int mood = HeartfeltConfig.CONFESSION_FAIL_MOOD.get();
                if (mood > 0) {
                    MaidMarriageCompat.addMood(maid, -mood);
                }
                // v1.5.19:关系破裂 → 清除吃醋压制标记(仅当已无任何确认关系女仆;
                // 结婚永久不离婚,有其他关系女仆则保留)
                RelationshipExemption.clearDedicatedIfNone(player);
                player.m_213846_(Component.m_237113_(
                        PromptTexts.heartbroken(maid.m_7755_().getString())));
            }
        }
    }

    // ==================== 主动告白尝试 ====================

    /**
     * v1.5.365:女仆主动告白被拒(剧本"……先让我缓缓"= confession_reject 选项)。
     * maidmarriage 的拒绝选项本身【没有服务端动作】(接受选项才有 story_confession_accept
     * 事件)——所以真实拒绝对记忆/情绪零影响(用户:"告白失败时对记忆竟然一点影响都没有!
     * 使用手册标记可以生效,真的告白失败在主动告白中选择缓一缓,对情绪以及记忆竟然真的一点影响都没有")。
     * 由客户端 ConfessionRejectPacket(C2S)触发:写永久记忆 CONFESSION_FAILED
     * (她之后不再主动告白——isEligible 门 + AI 记忆 MEMORY_CONFESSION_FAILED + 后果文本)
     * + 心情惩罚(CONFESSION_FAIL_MOOD)+ 系统消息。
     */
    public static void handleConfessionRejected(ServerPlayer player, EntityMaid maid) {
        if (maid.getPersistentData().m_128471_(HeartfeltTags.CONFESSION_FAILED)) {
            return; // 幂等:已标记过(调整器手动标记/重复拒绝)
        }
        long now = maid.m_9236_().m_46467_();
        maid.getPersistentData().m_128379_(HeartfeltTags.CONFESSION_FAILED, true);
        maid.getPersistentData().m_128356_(HeartfeltTags.CONFESSION_FAILED_AT, now);
        // v1.5.98:情感引擎喂脉冲——告白被拒 → 低落 + 修复债
        com.heartfelt.connection.affect.AffectStateManager.onConfessionFailed(maid);
        int mood = HeartfeltConfig.CONFESSION_FAIL_MOOD.get();
        if (mood > 0) {
            MaidMarriageCompat.addMood(maid, -mood);
        }
        player.m_213846_(Component.m_237113_(
                PromptTexts.confessionRejected(maid.m_7755_().getString())));
    }

    /** 周期扫描:未确认关系的高好感女仆按概率尝试主动告白 */
    private void attemptConfessions(MinecraftServer server) {
        long now = server.m_129921_();
        int interval = HeartfeltConfig.CONFESSION_ATTEMPT_INTERVAL.get();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            // 已宣布关系(已婚/有恋人)→ 不尝试(其他暗恋者此窗口静默)
            if (hasDeclaredPartner(player)) {
                continue;
            }
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                if (!isEligible(maid)) {
                    continue;
                }
                // 去重:距上次尝试不足一个窗口则跳过(关窗暂缓后不会立刻再弹)
                long last = maid.getPersistentData().m_128454_(HeartfeltTags.LAST_CONFESSION_TICK);
                if (last > 0L && now - last < interval) {
                    continue;
                }
                // 概率:base + (favor-192)/(384-192) × bonus(满好感 = base+bonus)
                int required = HeartfeltConfig.CONFESSION_REQUIRED_FAVOR.get();
                int favor = maid.getFavorability();
                double chance = HeartfeltConfig.CONFESSION_BASE_CHANCE.get()
                        + (favor - required) / (double) Math.max(1, 384 - required)
                        * HeartfeltConfig.CONFESSION_FAVOR_BONUS.get();
                chance = Math.max(0.0, Math.min(1.0, chance));
                if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) {
                    continue;
                }
                // 威胁检查(v1.5.8 增强):玩家与女仆周围大半径内均无敌对生物,
                // 女仆不在战斗中——"至少先把敌人都打掉才能告白"
                if (!DialogueDispatcher.isSafeArea(player, maid,
                        HeartfeltConfig.CONFESSION_THREAT_RADIUS.get())) {
                    continue;
                }
                // 距离检查(真实距离,格;太远则暂缓)
                // v1.5.17:修复 m_20280_ 是平方距离——旧版与线性 32 格直接比较,
                // 实际只有 √32≈5.7 格内才尝试(主人离女仆稍远永远不触发=难触发根因)
                double maxDist = HeartfeltConfig.CONFESSION_MAX_DISTANCE.get();
                if (maid.m_20280_(player) > maxDist * maxDist) {
                    continue;
                }
                // v1.5.0:不再直接拉对话框——启动【前摇】:
                // 系统消息提示 → 女仆走向玩家 → 走到身边才触发告白(ConfessionApproachManager)
                maid.getPersistentData().m_128356_(HeartfeltTags.LAST_CONFESSION_TICK, now);
                ConfessionApproachManager.startApproach(player, maid);
            }
        }
    }

    /** 尝试资格:好感≥触发线 且 未告白 且 未婚 且 非女儿 且 未失败过 */
    private static boolean isEligible(EntityMaid maid) {
        if (maid.getFavorability() < HeartfeltConfig.CONFESSION_REQUIRED_FAVOR.get()) {
            return false;
        }
        if (RelationshipExemption.isConfessed(maid) || RelationshipExemption.isMarried(maid)
                || RelationshipExemption.isChild(maid)) {
            return false;
        }
        return !maid.getPersistentData().m_128471_(HeartfeltTags.CONFESSION_FAILED);
    }

    /**
     * v1.5.100:手册「立即触发主动表白」按钮——跳过概率/冷却,直接对玩家附近
     * 好感最高的【资格女仆】启动告白前摇(系统提示 → 走向 → 到身边弹告白)。
     * 资格复用 isEligible(好感≥触发线/未告白/未婚/非女儿/未失败);
     * 已宣布关系(已婚/有恋人)时拒绝——与周期扫描同语义。
     * 返回触发结果文案(非 null = 已触发,玩家系统消息反馈)。
     */
    public static String forceConfession(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        if (hasDeclaredPartner(player)) {
            return "已有确认关系(妻子/恋人),其他女仆不会告白。";
        }
        EntityMaid best = null;
        int bestFavor = -1;
        for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
            if (!isEligible(maid)) {
                continue;
            }
            int favor = maid.getFavorability();
            if (favor > bestFavor) {
                best = maid;
                bestFavor = favor;
            }
        }
        if (best == null) {
            return "附近 48 格内没有符合告白条件的女仆(需要好感≥"
                    + HeartfeltConfig.CONFESSION_REQUIRED_FAVOR.get()
                    + "、未告白、未婚、非女儿、且从未被拒过)。";
        }
        // 立即启动前摇(不经概率/冷却;到达判定由 ConfessionApproachManager 负责)
        best.getPersistentData().m_128356_(HeartfeltTags.LAST_CONFESSION_TICK,
                best.m_9236_().m_46467_());
        ConfessionApproachManager.startApproach(player, best);
        return "已触发 " + best.m_7755_().getString()
                + "(好感 " + bestFavor + ") 的告白前摇——她会走向你。";
    }

    /**
     * 该玩家是否已有【恋爱宣告】女仆(已婚/已告白)——已宣布则其他女仆不再尝试。
     * v1.5.20:改为【事件判定】——查主人玩家全局标记 heartfelt_dedicated(确认过
     * 关系即压制,不依赖配偶是否在场;妻子被收进魂符/暂时不在时旧版在场扫描
     * 落空、其他女仆又开始告白)。标记在全部确认关系解除时清除(v1.5.19)。
     */
    private static boolean hasDeclaredPartner(ServerPlayer player) {
        return RelationshipExemption.playerHasDedicated(player);
    }

    // ==================== P0:玩家主动告白(玩家开口 + 女仆回应) ====================

    /**
     * v1.3.0:玩家主动告白。客户端 heartfelt 告白屏发来 C2S,服务端判定女仆回应:
     * - 可告白(好感≥告白线、未告白、未婚、非女儿、非哀悼)→ 接受:
     *   completeConfession + 记录告白发起方(player)+ 心情小涨 + S2C 甜蜜回应;
     * - 哀悼期/已确认关系 → 委婉回应(不惩罚,不写失败标记);
     * - 不满足告白条件 → 系统提示"还没准备好"。
     */
    public static void handlePlayerConfession(ServerPlayer player, EntityMaid maid) {
        if (maid == null || !maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
            return;
        }
        long now = maid.m_9236_().m_46467_();
        boolean canConfess = maid.getFavorability() >= HeartfeltConfig.FREEZE_CONFESSION_LINE.get()
                && !RelationshipExemption.isConfessed(maid) && !RelationshipExemption.isMarried(maid)
                && !RelationshipExemption.isChild(maid);
        if (!canConfess) {
            player.m_213846_(Component.m_237113_(
                    PromptTexts.playerConfessionNotReady(maid.m_7755_().getString())));
            return;
        }
        // 接受:完成告白 + 事件历史(发起方=玩家)
        MaidMarriageCompat.completeConfession(maid);
        // v1.5.98:情感引擎喂脉冲——告白成功 → 亲密大涨
        com.heartfelt.connection.affect.AffectStateManager.onConfessionSuccess(maid);
        // v1.5.18:确认关系 → 写主人全局标记(绝对压制吃醋)
        RelationshipExemption.markDedicated(player);
        maid.getPersistentData().m_128359_(HeartfeltTags.CONFESSION_BY, "player");
        maid.getPersistentData().m_128356_(HeartfeltTags.CONFESSION_AT, now);
        MaidMarriageCompat.addMood(maid, 2);
        HeartfeltNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player),
                new HeartfeltNetwork.PlayerConfessionResultPacket(maid.m_20148_(), true,
                        PromptTexts.playerConfessionAccepted(maid.m_7755_().getString())));
    }
}
