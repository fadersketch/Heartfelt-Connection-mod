package com.heartfelt.connection.affect;

/**
 * 情感关系阶段推导(v1.5.98,移植 MaidSoulCore RelationshipHmm 的简化版)。
 * 按事件类型 + 当前连续情感 → 推导情感阶段(初识/甜蜜/热烈/稳定/冷淡/修复中)。
 *
 * 与 maidmarriage 确认关系(妻子/恋人/女儿)正交:它是"她此刻怎么看待
 * 这段关系"的动态档位,由事件驱动,供 prompt 注入语气。
 */
final class RelationshipHmm {

    RelationshipStage observe(RelationshipStage current, AffectEventKind event,
            double intimacy, double conflict, int positiveStreak) {
        RelationshipStage safe = current == null ? RelationshipStage.COURTING : current;
        if (event == AffectEventKind.MAID_HURT_BY_OWNER) {
            return conflict > 0.18 ? RelationshipStage.COLD : RelationshipStage.REPAIRING;
        }
        if (event == AffectEventKind.OWNER_APOLOGY) {
            return conflict > 0.2 ? RelationshipStage.REPAIRING
                    : this.sweetOrStable(intimacy, positiveStreak);
        }
        if (event == AffectEventKind.OWNER_AFFECTION
                || event == AffectEventKind.INTIMATE_INTERACTION
                || event == AffectEventKind.CONFESSION_SUCCESS
                || event == AffectEventKind.HOLIDAY_CELEBRATION) {
            if (conflict > 0.48) {
                return RelationshipStage.REPAIRING;
            }
            return this.sweetOrStable(intimacy, positiveStreak);
        }
        if (event == AffectEventKind.HEARTBROKEN) {
            return RelationshipStage.COLD;
        }
        if (event == AffectEventKind.QUIET_RECOVERY) {
            return this.stepQuiet(safe, intimacy, conflict);
        }
        if (conflict > 0.6) {
            return RelationshipStage.COLD;
        }
        if (safe == RelationshipStage.REPAIRING && conflict < 0.18) {
            return RelationshipStage.STABLE;
        }
        if (safe == RelationshipStage.COLD && conflict < 0.28 && intimacy > 0.42) {
            return RelationshipStage.REPAIRING;
        }
        return safe;
    }

    private RelationshipStage sweetOrStable(double intimacy, int positiveStreak) {
        if (intimacy >= 0.76 && positiveStreak >= 4) {
            return RelationshipStage.PASSIONATE;
        }
        if (intimacy >= 0.56 && positiveStreak >= 2) {
            return RelationshipStage.SWEET;
        }
        if (intimacy >= 0.42) {
            return RelationshipStage.STABLE;
        }
        return RelationshipStage.COURTING;
    }

    private RelationshipStage stepQuiet(RelationshipStage current, double intimacy, double conflict) {
        if (conflict > 0.5) {
            return RelationshipStage.COLD;
        }
        if (current == RelationshipStage.PASSIONATE && intimacy < 0.66) {
            return RelationshipStage.SWEET;
        }
        if (current == RelationshipStage.SWEET && intimacy < 0.48) {
            return RelationshipStage.STABLE;
        }
        if (current == RelationshipStage.COLD && conflict < 0.34) {
            return RelationshipStage.REPAIRING;
        }
        return current;
    }
}
