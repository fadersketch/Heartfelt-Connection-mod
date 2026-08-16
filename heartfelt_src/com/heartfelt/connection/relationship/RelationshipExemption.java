package com.heartfelt.connection.relationship;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 关系判定核心(v1.1.0 全面重构:保留门面,实现委托 MaidMarriageCompat)。
 *
 * 三层语义(用户确认的设计):
 * - isFrozen(自身冻结层):恐惧/信任冻结、不背叛不淡忘、自己不争风吃醋——
 *   结婚 / 告白完成(恋人)/ 好感≥192(深爱未告白)/ 父女 任一成立即冻结。
 * - isDedicated(恋爱宣告层):已婚 / 告白完成——主人名花有主,全体女仆不再吃醋。
 * - strictRelationKey(关系栏):只有【确认关系】(妻子/恋人/女儿)才返回标签。
 *
 * v1.1.0 变更:
 * - TaskData 读取/accessor 缓存/产后判定迁入 MaidMarriageCompat(A1 mood_data 修复、
 *   A8 改调 PregnancyData.isInPostpartumRecovery)
 * - ownerHasDedicatedMaid 改 UUID 比较(A3)
 */
public final class RelationshipExemption {
    private RelationshipExemption() {
    }

    /** 自身冻结层:结婚 / 告白 / 好感≥192 / 父女(恐惧信任冻结、不背叛不淡忘、自己不争风吃醋) */
    public static boolean isFrozen(EntityMaid maid) {
        return isPartner(maid) || isChild(maid);
    }

    /** 恋爱宣告层:已婚 或 告白完成——全体吃醋杜绝(主人名花有主) */
    public static boolean isDedicated(EntityMaid maid) {
        return isMarried(maid) || isConfessed(maid);
    }

    /**
     * 伴侣(v1.2.1 边界重定义):【确认关系】才冻结——已婚(无条件)/ 恋人(需好感≥告白线,
     * 跌破即破裂)。
     * 深爱暗恋(好感≥192 未告白)**不再冻结**:她的信任/恐惧恢复活跃,受好感度中和缓和
     * (EmotionSmoothingManager)自然推向忠诚数值——冻结与"忠诚判定"只属于确认关系。
     */
    public static boolean isPartner(EntityMaid maid) {
        if (isMarried(maid)) {
            return true; // 婚姻是永久契约,冻结无条件(破裂不在 v1.2.x 范围)
        }
        if (isConfessed(maid)) {
            // 恋人冻结需要好感 ≥ 告白线(128)——跌破即冻结解除,配合破裂检查
            try {
                return maid.getFavorability() >= HeartfeltConfig.FREEZE_CONFESSION_LINE.get();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** 与主人结婚(maidmarriage marriage_data.married()) */
    public static boolean isMarried(EntityMaid maid) {
        return MaidMarriageCompat.readBool(maid, "marriage_data", "married");
    }

    /** 恋人:告白完成(relationship_progress_data.confessionCompleted) */
    public static boolean isConfessed(EntityMaid maid) {
        return MaidMarriageCompat.readBool(maid, "relationship_progress_data", "confessionCompleted");
    }

    /** 是主人的女儿(child_state_data.child() 且 father==主人,含血统数据回退) */
    public static boolean isChild(EntityMaid maid) {
        Object data = MaidMarriageCompat.readTaskData(maid, "child_state_data");
        if (data == null || !MaidMarriageCompat.readBool(data, "child")) {
            return false;
        }
        UUID ownerId = maid.m_269323_() != null ? maid.m_269323_().m_20148_() : null;
        UUID fatherId = MaidMarriageCompat.readUuid(data, "father");
        if (fatherId == null) {
            fatherId = MaidMarriageCompat.readUuid(maid, "child_lineage_data", "father");
        }
        return ownerId != null && fatherId != null && fatherId.equals(ownerId);
    }

    /** 怀孕中 或 产后恢复期(maidmarriage pregnancy_data;A8 改调 isInPostpartumRecovery) */
    public static boolean isPregnantOrPostpartum(EntityMaid maid, long gameTime) {
        Object data = MaidMarriageCompat.readTaskData(maid, "pregnancy_data");
        if (data == null) {
            return false;
        }
        if (MaidMarriageCompat.readBool(data, "pregnant")) {
            return true;
        }
        return MaidMarriageCompat.isInPostpartumRecovery(maid, gameTime);
    }

    /** 女儿的母亲 UUID(priority:child_lineage_data.mother → child_state_data.mother);无返回 null */
    public static UUID readMotherUuid(EntityMaid maid) {
        UUID mother = MaidMarriageCompat.readUuid(maid, "child_lineage_data", "mother");
        if (mother != null) {
            return mother;
        }
        return MaidMarriageCompat.readUuid(maid, "child_state_data", "mother");
    }

    /**
     * 关系栏标签(<context> 用):只有【确认关系】才返回——wife / lover / daughter;
     * 无确认关系返回 null(高好感未告白不算,走原 favorability 流程)。
     */
    public static String strictRelationKey(EntityMaid maid) {
        if (isMarried(maid)) {
            return "wife";
        }
        if (isChild(maid)) {
            return "daughter";
        }
        if (isConfessed(maid)) {
            return "lover";
        }
        return null;
    }

    /** 关系中文标签(广播/记忆可视化用):妻子 / 女儿 / 恋人;无则 null */
    public static String relationLabel(EntityMaid maid) {
        String key = strictRelationKey(maid);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "wife" -> "妻子";
            case "daughter" -> "女儿";
            default -> "恋人";
        };
    }

    /**
     * 主人的女仆中是否存在【恋爱宣告】女仆(妻子/恋人,不含自己)——用于吃醋整体隔离。
     * v1.5.18 绝对压制:主人玩家持久标记 heartfelt_dedicated 已置位 → 直接 true
     * (确认过关系就永久压制吃醋,不再依赖配偶是否在场——被收进魂符/暂时不在
     * 时旧版在场扫描落空、吃醋恢复)。标记未置时做原有 32 格扫描,发现已确认
     * 关系女仆即自愈写标记。
     */
    public static boolean ownerHasDedicatedMaid(EntityMaid maid) {
        LivingEntity owner = maid.m_269323_();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        return playerHasDedicated(player);
    }

    /**
     * v1.5.20:玩家级"已有确认关系"判定(事件判定,非角色/在场判定)——查主人
     * 玩家全局标记 heartfelt_dedicated:已置位直接 true(确认过关系即压制,
     * 不依赖配偶是否在场,与吃醋隔离同语义);未置时在场扫描自愈写标记。
     */
    public static boolean playerHasDedicated(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.getPersistentData().m_128471_(HeartfeltTags.HEARTFELT_DEDICATED)) {
            return true;
        }
        try {
            for (EntityMaid m : DialogueDispatcher.maidsOf(player, 48)) {
                if (isDedicated(m)) {
                    markDedicated(player); // 自愈:发现已确认关系女仆 → 写全局标记
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** v1.5.18:置"已确认关系"全局标记(主人玩家 ForgeData,永不清除——绝对压制) */
    public static void markDedicated(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.getPersistentData().m_128379_(HeartfeltTags.HEARTFELT_DEDICATED, true);
        } catch (Exception ignored) {
        }
    }

    /** v1.5.18:周期扫描——该玩家任一女仆已确认关系则置全局标记(覆盖 maidmarriage
     *  自己的 UI 确认路径:关系女仆在场时 10 秒内补上标记) */
    public static void sweepDedicated(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            for (EntityMaid m : DialogueDispatcher.maidsOf(player, 48)) {
                if (isDedicated(m)) {
                    markDedicated(player);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * v1.5.19:关系破裂时清除吃醋压制标记——但仅当该玩家已无任何确认关系女仆
     * (结婚是永久契约、本 mod 不离婚;只要还有其他确认关系女仆如妻子,标记保留)。
     */
    public static void clearDedicatedIfNone(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            for (EntityMaid m : DialogueDispatcher.maidsOf(player, 48)) {
                if (isDedicated(m)) {
                    return; // 还有其他确认关系女仆——保留标记
                }
            }
        } catch (Exception ignored) {
        }
        try {
            player.getPersistentData().m_128473_(HeartfeltTags.HEARTFELT_DEDICATED);
        } catch (Exception ignored) {
        }
    }

    /** 门面转发:读 maidmarriage 的 TaskData 数据(StoryMemoryManager 复用) */
    public static Object readTaskData(EntityMaid maid, String keyName) {
        return MaidMarriageCompat.readTaskData(maid, keyName);
    }
}
