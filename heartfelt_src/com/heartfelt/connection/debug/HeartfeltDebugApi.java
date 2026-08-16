package com.heartfelt.connection.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.combat.AttackBetrayerBehavior;
import com.heartfelt.connection.combat.BetrayalRedemptionManager;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.compat.ReflectUtil;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调试/信息提供 API(v1.1.0 新增;包名 com.heartfelt.connection.debug —— 契约固定)。
 *
 * 供 Promaid(com.maidsmart)的手册与详细设置界面反射调用,与 heartfelt 调
 * Promaid 的 ApiQuotaManager 完全对称,双方零硬依赖。
 *
 * 契约(勿改签名):
 *   isLoaded() / version() / featureSummary() / maidDebug(EntityMaid)
 *   clearHatedPlayer(EntityMaid) / forceRedemption(EntityMaid, Player)
 */
public final class HeartfeltDebugApi {
    private HeartfeltDebugApi() {
    }

    /** 补丁是否装载 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("heartfelt_connection");
    }

    public static String version() {
        return ModList.get().getModContainerById("heartfelt_connection")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
    }

    /**
     * 运行时功能状态探测(而不是写死的清单):
     * 用类探活判断各依赖目标是否可解析,手册据此展示"哪些功能真正生效"。
     * 每一项:{"功能名", "ON/OFF"}
     */
    public static String[][] featureSummary() {
        return new String[][]{
                {"关系冻结(恐惧/信任)", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionData")},
                {"背叛隔离", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionBetrayalManager")},
                {"遗忘隔离", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionForgettingManager")},
                {"吃醋隔离", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionDotingManager")},
                {"主动对话配额", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionActiveDialogue")},
                {"见证对话配额", probe(HeartfeltTags.CR_PACKAGE + ".emotion.EmotionPassiveManager")},
                {"饥饿档缓存", probe(HeartfeltTags.CR_PACKAGE + ".hunger.HungerManager")},
                {"怀孕饥饿钳制", probe(HeartfeltTags.CR_PACKAGE + ".hunger.HungerData")},
                {"背叛和解(悔改)", "ON"},
                {"家庭保护", "ON"},
                {"工作坐姿兼容", probe(HeartfeltTags.MM_COMPAT + ".MaidWorkManager")},
                {"Promaid 配额", probe("com.maidsmart.dialogue.ApiQuotaManager")},
        };
    }

    /**
     * 单女仆调试信息,按行返回。
     * 顺序:关系标签/阶段/好感/冻结/背叛/悔改进度/仇恨玩家/悼念剩余/怀孕产后/心情/情绪值
     */
    public static String[] maidDebug(EntityMaid maid) {
        if (maid == null) {
            return new String[]{"未选中女仆"};
        }
        List<String> lines = new ArrayList<>();
        try {
            String relation = RelationshipExemption.relationLabel(maid);
            lines.add("关系: " + (relation == null ? "未确认" : relation));
            Object stage = ReflectUtil.invokeStatic(ReflectUtil.staticMethod(
                    HeartfeltTags.MM_COMPAT + ".MaidRelationshipManager", "resolveStage", EntityMaid.class), maid);
            lines.add("阶段: " + (stage == null ? "?" : stage));
            lines.add("好感: " + maid.getFavorability() + "/384");
            lines.add("冻结: " + (RelationshipExemption.isFrozen(maid) ? "是" : "否"));
            lines.add("背叛: " + (AttackBetrayerBehavior.isBetraying(maid) ? "是" : "否"));
            // v1.2.0:主动告白状态
            if (maid.getPersistentData().m_128471_(HeartfeltTags.CONFESSION_FAILED)) {
                lines.add("告白: 已被拒(不再主动)");
            } else if (!RelationshipExemption.isConfessed(maid) && !RelationshipExemption.isMarried(maid)) {
                lines.add("告白: 可主动(好感" + maid.getFavorability() + ")");
            } else {
                lines.add("告白: 已确认");
            }
            if (maid.getPersistentData().m_128454_(HeartfeltTags.HEARTBROKEN_AT) > 0L) {
                lines.add("心碎: 是");
            }
            int progress = maid.getPersistentData().m_128451_(HeartfeltTags.REDEMPTION_PROGRESS);
            if (progress > 0) {
                lines.add("悔改安抚: " + progress + "/" + HeartfeltConfig.REDEMPTION_FEEDS.get());
            }
            UUID hated = maid.getPersistentData().m_128342_(HeartfeltTags.HATED_PLAYER);
            lines.add("仇恨玩家: " + (hated == null ? "无" : shortUuid(hated)));
            lines.add("怀孕/产后: " + (RelationshipExemption.isPregnantOrPostpartum(maid, maid.m_9236_().m_46467_()) ? "是" : "否"));
            int mood = com.heartfelt.connection.compat.MaidMarriageCompat.moodValue(maid);
            if (mood >= 0) {
                lines.add("心情: " + mood + "/25");
            }
            LivingEntity owner = maid.m_269323_();
            int[] emotion = owner != null
                    ? CallResponseCompat.emotionValues(maid, owner.m_20148_())
                    : null;
            if (emotion != null) {
                lines.add("信任/恐惧: " + emotion[0] + "/" + emotion[1]);
            }
            // v1.5.13：纪念日里程碑 + Promaid 联动状态（同一女仆 persistentData 直读，零依赖）
            long confessionAt = maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT);
            long firstMeetAt = maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET);
            long baseDay = confessionAt > 0L ? confessionAt / 24000L
                    : (firstMeetAt > 0L ? firstMeetAt / 24000L : 0L);
            long day = maid.m_9236_().m_46467_() / 24000L;
            if (baseDay <= 0L) {
                lines.add("纪念日: 无基准(未告白/初遇)");
            } else {
                long lastDay = maid.getPersistentData().m_128454_(HeartfeltTags.LAST_ANNIVERSARY_DAY);
                long pmMark = maid.getPersistentData().m_128454_("maid_smart_anniv_mark");
                long appMark = maid.getPersistentData().m_128454_("maid_smart_anniv_app");
                long elapsed = day - baseDay;
                long[] marks = {7L, 30L, 100L, 365L};
                long next = 0L;
                for (long m : marks) {
                    if (m > pmMark) {
                        next = m;
                        break;
                    }
                }
                lines.add("纪念日: 基准=" + (confessionAt > 0L ? "告白" : "初遇")
                        + "·第" + elapsed + "天 · 上回触发日=" + (lastDay > 0L ? lastDay : "无"));
                lines.add("Promaid联动: 达成游标=" + (pmMark > 0L ? pmMark + "天" : "无")
                        + " · 临近游标=" + (appMark > 0L ? appMark + "天" : "无")
                        + (next > 0L ? " · 下个里程碑=" + next + "天(距" + Math.max(0, next - elapsed) + "天)"
                        : " · 全部里程碑已达成"));
            }
        } catch (Exception ignored) {
            lines.add("调试信息解析失败");
        }
        return lines.toArray(new String[0]);
    }

    // ==================== 调试动作(有副作用,均为幂等操作) ====================

    /** 清除女仆的仇恨玩家标记(A2 残留问题) */
    public static boolean clearHatedPlayer(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        try {
            maid.getPersistentData().m_128473_(HeartfeltTags.HATED_PLAYER);
            maid.getPersistentData().m_128473_(HeartfeltTags.HATED_AT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 强制悔改:解除背叛状态 + 认当前互动玩家为主(调试用,需服务端) */
    public static boolean forceRedemption(EntityMaid maid, Player player) {
        if (maid == null || player == null) {
            return false;
        }
        try {
            BetrayalRedemptionManager.completeRedemptionForDebug(maid, player);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** v1.2.0:清除"告白失败"标记——让她可以重新主动告白(调试/后悔药) */
    public static boolean resetConfessionFailure(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        try {
            maid.getPersistentData().m_128473_(HeartfeltTags.CONFESSION_FAILED);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 内部 ====================

    private static String probe(String className) {
        return ReflectUtil.load(className) != null ? "ON" : "OFF";
    }

    private static String shortUuid(UUID uuid) {
        String s = uuid.toString();
        return s.substring(0, 8) + "…";
    }
}
