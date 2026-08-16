package com.heartfelt.connection.affect;

/**
 * 情感引擎(v1.5.98,移植 MaidSoulCore AffectEngine 精简版)。
 * 事件 → 多维脉冲:每次 apply 按事件种类对 VAD/亲密/冲突/债/思慕做加减,
 * 再用 RelationshipHmm 推导情感阶段,归一化后落 NBT。
 */
public final class AffectEngine {
    private final RelationshipHmm relationshipHmm = new RelationshipHmm();

    public void apply(AffectProfile profile, AffectEventKind kind, int intensity) {
        if (profile == null || kind == null) {
            return;
        }
        profile.normalize();
        double weight = Math.max(0.0, Math.min(1.0, (double) intensity / 100.0));
        this.applyEventImpulse(profile, kind, weight);
        profile.relationshipStage = this.relationshipHmm.observe(
                profile.stage(), kind, profile.intimacy, profile.conflict,
                profile.positiveEventStreak).id();
        profile.lastEvent = kind.id();
        profile.normalize();
    }

    private void applyEventImpulse(AffectProfile profile, AffectEventKind kind, double weight) {
        switch (kind) {
            case OWNER_AFFECTION -> {
                profile.valence = clampSigned(profile.valence + 0.16 * weight);
                profile.arousal = clamp01(profile.arousal + 0.08 * weight);
                profile.dominance = clamp01(profile.dominance + 0.02 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.11 * weight);
                profile.conflict = clamp01(profile.conflict - 0.05 * weight);
                profile.longing = clamp01(profile.longing + 0.07 * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.03 * weight);
                profile.positiveEventStreak++;
            }
            case INTIMATE_INTERACTION -> {
                profile.valence = clampSigned(profile.valence + 0.12 * weight);
                profile.arousal = clamp01(profile.arousal + 0.1 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.08 * weight);
                profile.conflict = clamp01(profile.conflict - 0.04 * weight);
                profile.longing = clamp01(profile.longing + 0.05 * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.04 * weight);
                profile.positiveEventStreak++;
            }
            case OWNER_APOLOGY -> {
                profile.valence = clampSigned(profile.valence + 0.1 * weight);
                profile.arousal = clamp01(profile.arousal + 0.05 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.06 * weight);
                profile.conflict = clamp01(profile.conflict - 0.16 * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt - 0.14 * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.18 * weight);
                profile.positiveEventStreak++;
            }
            case MAID_HURT_BY_OWNER -> {
                profile.valence = clampSigned(profile.valence - 0.22 * weight);
                profile.arousal = clamp01(profile.arousal + 0.2 * weight);
                profile.dominance = clamp01(profile.dominance - 0.1 * weight);
                profile.intimacy = clamp01(profile.intimacy - 0.15 * weight);
                profile.conflict = clamp01(profile.conflict + 0.24 * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.22 * weight);
                profile.repairDebt = clamp01(profile.repairDebt + 0.26 * weight);
                profile.positiveEventStreak = 0;
            }
            case MAID_HURT_BY_WORLD -> {
                profile.valence = clampSigned(profile.valence - 0.08 * weight);
                profile.arousal = clamp01(profile.arousal + 0.12 * weight);
                profile.dominance = clamp01(profile.dominance - 0.04 * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.1 * weight);
                profile.longing = clamp01(profile.longing + 0.04 * weight);
            }
            case OWNER_DISTRESS -> {
                profile.valence = clampSigned(profile.valence - 0.03 * weight);
                profile.arousal = clamp01(profile.arousal + 0.08 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.045 * weight);
                profile.longing = clamp01(profile.longing + 0.08 * weight);
                profile.positiveEventStreak++;
            }
            case OWNER_LONG_ABSENCE -> {
                profile.longing = clamp01(profile.longing + 0.28 * weight);
                profile.valence = clampSigned(profile.valence + 0.06 * weight);
                profile.arousal = clamp01(profile.arousal + 0.1 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.04 * weight);
            }
            case CONFESSION_SUCCESS -> {
                profile.valence = clampSigned(profile.valence + 0.24 * weight);
                profile.arousal = clamp01(profile.arousal + 0.14 * weight);
                profile.dominance = clamp01(profile.dominance + 0.04 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.22 * weight);
                profile.conflict = clamp01(profile.conflict - 0.08 * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.06 * weight);
                profile.positiveEventStreak += 2;
            }
            case CONFESSION_FAILED -> {
                profile.valence = clampSigned(profile.valence - 0.2 * weight);
                profile.arousal = clamp01(profile.arousal + 0.12 * weight);
                profile.intimacy = clamp01(profile.intimacy - 0.08 * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.12 * weight);
                profile.repairDebt = clamp01(profile.repairDebt + 0.18 * weight);
                profile.positiveEventStreak = 0;
            }
            case HEARTBROKEN -> {
                profile.valence = clampSigned(profile.valence - 0.3 * weight);
                profile.arousal = clamp01(profile.arousal + 0.16 * weight);
                profile.intimacy = clamp01(profile.intimacy - 0.2 * weight);
                profile.conflict = clamp01(profile.conflict + 0.3 * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.3 * weight);
                profile.positiveEventStreak = 0;
            }
            case HOLIDAY_CELEBRATION -> {
                profile.valence = clampSigned(profile.valence + 0.1 * weight);
                profile.arousal = clamp01(profile.arousal + 0.08 * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.06 * weight);
                profile.positiveEventStreak++;
            }
            case QUIET_RECOVERY -> this.stepTime(profile, weight);
        }
    }

    /** 安静时间:向当前阶段的基线缓慢回退(冲突/债消退,思慕升高) */
    private void stepTime(AffectProfile profile, double weight) {
        double[] baseline = stageBaseline(profile.stage());
        profile.valence = approach(profile.valence, baseline[0], 0.045 * weight);
        profile.arousal = approach(profile.arousal, baseline[1], 0.04 * weight);
        profile.dominance = approach(profile.dominance, baseline[2], 0.03 * weight);
        profile.conflict = clamp01(approach(profile.conflict, 0.04, 0.03 * weight));
        profile.hurtDebt = clamp01(approach(profile.hurtDebt, 0.0, 0.02 * weight));
        profile.repairDebt = clamp01(approach(profile.repairDebt, 0.0, 0.02 * weight));
        profile.longing = clamp01(approach(profile.longing,
                0.42 + profile.intimacy * 0.24, 0.025 * weight));
    }

    private static double[] stageBaseline(RelationshipStage stage) {
        return switch (stage) {
            case SWEET -> new double[]{0.62, 0.46, 0.5};
            case PASSIONATE -> new double[]{0.72, 0.64, 0.56};
            case STABLE -> new double[]{0.36, 0.3, 0.5};
            case COLD -> new double[]{-0.38, 0.48, 0.34};
            case REPAIRING -> new double[]{0.02, 0.46, 0.42};
            default -> new double[]{0.12, 0.34, 0.48};
        };
    }

    private static double approach(double value, double target, double step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }

    private static double clamp01(double value) {
        return AffectProfile.clamp01(value);
    }

    private static double clampSigned(double value) {
        return AffectProfile.clamp(value, -1.0, 1.0);
    }
}
