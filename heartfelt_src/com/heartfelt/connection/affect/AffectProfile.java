package com.heartfelt.connection.affect;

import net.minecraft.nbt.CompoundTag;

/**
 * 连续情感快照(v1.5.98,移植 MaidSoulCore AffectProfile)——多维连续状态,
 * 落女仆 NBT(heartfelt_affect),收魂符/跨维度实体重建不丢。
 *
 * 维度:
 * - VAD:valence(愉悦)/arousal(唤醒)/dominance(支配),-1~1 / 0~1 / 0~1;
 * - intimacy(亲密)/ conflict(冲突)/ hurtDebt(受伤债)/ repairDebt(修复债)/
 *   longing(思慕),全部 0~1;
 * - positiveEventStreak(连续正向事件计数);
 * - relationshipStage(情感关系阶段,由 RelationshipHmm 推导);
 * - emotionLabel(主导情绪,由 VAD+亲密推导)。
 *
 * 与现有机制的关系:不影响 maidmarriage 的确认关系(妻子/恋人/女儿)与
 * TLM 好感——它只是"她此刻情绪"的额外连续维度,注入 LLM prompt。
 */
public final class AffectProfile {
    public double valence = 0.18;
    public double arousal = 0.32;
    public double dominance = 0.48;
    public double intimacy = 0.28;
    public double conflict = 0.06;
    public double hurtDebt = 0.0;
    public double repairDebt = 0.0;
    public double longing = 0.42;
    public int positiveEventStreak = 0;
    public String relationshipStage = RelationshipStage.COURTING.id();
    public String emotionLabel = EmotionLabel.NEUTRAL.id();
    public String lastEvent = "";

    // ---- NBT 持久化 ----

    private static final String KEY = "heartfelt_affect";

    public static AffectProfile fromTag(CompoundTag tag) {
        AffectProfile p = new AffectProfile();
        if (tag == null || !tag.m_128425_(KEY, 10)) { // 10 = CompoundTag
            return p;
        }
        CompoundTag n = tag.m_128469_(KEY);
        p.valence = n.m_128459_("valence");
        p.arousal = n.m_128459_("arousal");
        p.dominance = n.m_128459_("dominance");
        p.intimacy = n.m_128459_("intimacy");
        p.conflict = n.m_128459_("conflict");
        p.hurtDebt = n.m_128459_("hurtDebt");
        p.repairDebt = n.m_128459_("repairDebt");
        p.longing = n.m_128459_("longing");
        p.positiveEventStreak = n.m_128451_("streak");
        p.relationshipStage = n.m_128461_("stage");
        p.emotionLabel = n.m_128461_("emotion");
        p.lastEvent = n.m_128461_("lastEvent");
        return p;
    }

    public void saveTo(CompoundTag tag) {
        CompoundTag n = new CompoundTag();
        n.m_128347_("valence", this.valence);
        n.m_128347_("arousal", this.arousal);
        n.m_128347_("dominance", this.dominance);
        n.m_128347_("intimacy", this.intimacy);
        n.m_128347_("conflict", this.conflict);
        n.m_128347_("hurtDebt", this.hurtDebt);
        n.m_128347_("repairDebt", this.repairDebt);
        n.m_128347_("longing", this.longing);
        n.m_128405_("streak", this.positiveEventStreak);
        n.m_128359_("stage", this.relationshipStage);
        n.m_128359_("emotion", this.emotionLabel);
        n.m_128359_("lastEvent", this.lastEvent);
        tag.m_128365_(KEY, n);
    }

    public void normalize() {
        this.valence = clamp(this.valence, -1.0, 1.0);
        this.arousal = clamp01(this.arousal);
        this.dominance = clamp01(this.dominance);
        this.intimacy = clamp01(this.intimacy);
        this.conflict = clamp01(this.conflict);
        this.hurtDebt = clamp01(this.hurtDebt);
        this.repairDebt = clamp01(this.repairDebt);
        this.longing = clamp01(this.longing);
        this.positiveEventStreak = Math.max(0, this.positiveEventStreak);
        this.emotionLabel = EmotionLabel.fromVad(
                this.valence, this.arousal, this.dominance, this.intimacy).id();
    }

    public RelationshipStage stage() {
        return RelationshipStage.fromId(this.relationshipStage);
    }

    public EmotionLabel emotion() {
        return EmotionLabel.fromId(this.emotionLabel);
    }

    // ---- 注入 prompt 的文本 ----

    /** 简洁状态行(注入 LLM prompt 用,中文) */
    public String brief() {
        return "情绪=" + this.emotion().zhName()
                + ",情感阶段=" + this.stage().zhName()
                + ",亲密=" + percent(this.intimacy)
                + ",冲突=" + percent(this.conflict)
                + ",受伤=" + percent(this.hurtDebt)
                + ",修复=" + percent(this.repairDebt)
                + ",思念=" + percent(this.longing)
                + (this.lastEvent == null || this.lastEvent.isBlank() ? "" : ",最近事件=" + this.lastEvent);
    }

    /** 表达建议(让 LLM 按情绪贴合语气) */
    public String replyStyleAdvice() {
        if (this.hurtDebt >= 0.55 || this.conflict >= 0.55) {
            return "她仍有明显受伤或防备,回复要承认情绪余波,不能直接甜蜜重置。";
        }
        if (this.repairDebt >= 0.3 || this.stage() == RelationshipStage.REPAIRING) {
            return "她正在修复关系,语气应温柔但谨慎,先接住当前话题再慢慢靠近。";
        }
        if (this.stage() == RelationshipStage.SWEET
                || this.stage() == RelationshipStage.PASSIONATE
                || this.emotion() == EmotionLabel.LOVE) {
            return "她很喜欢主人,表达可以更柔软、黏人、愿意贴近,但仍要回应最新输入。";
        }
        if (this.valence <= -0.35) {
            return "她心情偏低,表达要短一些、软一些,避免跳到无关话题。";
        }
        return "她当前状态基本平稳,可以正常聊天,并保持温柔陪伴感。";
    }

    public static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int percent(double value) {
        return (int) Math.round(clamp01(value) * 100.0);
    }
}
