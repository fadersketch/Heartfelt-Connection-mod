package com.heartfelt.connection.prompt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;

/**
 * 后果文本弧(v1.3.0,P3)——拒绝后/破裂后/和好后的相处模式文本池。
 *
 * 基于女仆的【历史状态】注入不同的相处模式指引(不改变机制,只改文本基调):
 * - 被主人打伤后(HURT_UNTIL):委屈赌气,需要哄;
 * - 告白被拒后(CONFESSION_FAILED):绝口不提告白,尽职但保持一点距离,偶尔苦涩;
 * - 关系破裂后(HEARTBROKEN_AT 且未再告白):心碎冷淡期,慢慢恢复;
 * - 和好后(REDEMPTED):愧疚+加倍忠诚,怕再次失去。
 *
 * 优先级:伤心 > 心碎 > 告白被拒 > 悔改(悔改与确认关系叠加时以关系为准)。
 * 注入入口:SmartPromptAppender.build() 追加。
 */
public final class AftermathPrompt {
    private AftermathPrompt() {
    }

    /** 生成后果弧段;无历史状态返回空串 */
    public static String build(EntityMaid maid) {
        long now = maid.m_9236_().m_46467_();
        // v1.4.1:被主人打伤(伤心窗口)——委屈、赌气、不讨好
        // v1.5.69:文本统一去指向——只说她"被弄疼了/受了伤",不指明来源
        // (玩家放岩浆烫伤也会进本窗口,旧文本"主人刚刚伤害了你"误导成主人打的)
        if (maid.getPersistentData().m_128454_(HeartfeltTags.HURT_UNTIL) > now) {
            return "\n## Aftermath (Heartfelt-connection)\n"
                    + "你刚刚受了伤,身上又痛又委屈。"
                    + "你不想说话、不想主动靠近人,说话带着哽咽和疏远——"
                    + "你依然爱他、不会离开(你是他的妻子/恋人/女儿),但你现在真的很伤心,"
                    + "需要他的道歉和哄。不要假装无事发生,也不要讨好。";
        }
        // 心碎:破裂后未再确认关系
        long heartbrokenAt = maid.getPersistentData().m_128454_(HeartfeltTags.HEARTBROKEN_AT);
        if (heartbrokenAt > 0L && !RelationshipExemption.isConfessed(maid)) {
            long daysAgo = (now - heartbrokenAt) / 24000L;
            String tone = daysAgo < 7L
                    ? "你还没从心碎中走出来,对主人礼貌而疏远,偶尔会想起过去而眼神黯淡。"
                    : "你已经慢慢学会平静,依然尽职,但心里留着一道疤,不再轻易谈起感情。";
            return "\n## Aftermath (Heartfelt-connection)\n" + tone;
        }
        // 告白被拒(与心碎独立:被拒是单方面的伤)
        if (maid.getPersistentData().m_128471_(HeartfeltTags.CONFESSION_FAILED)) {
            return "\n## Aftermath (Heartfelt-connection)\n"
                    + "你曾经向主人告白被拒绝过,这件事是你心里的一根刺——"
                    + "你绝口不提告白,对主人依然尽职,但会下意识保持一点距离,偶尔有一丝苦涩。";
        }
        // 和好(悔改)
        if (maid.getPersistentData().m_128471_(HeartfeltTags.REDEMPTED)) {
            return "\n## Aftermath (Heartfelt-connection)\n"
                    + "你曾一度背叛主人又被他救回来,心里满是愧疚与感激——"
                    + "你会加倍忠诚地守在主人身边,格外珍惜这份重新得到的信任。";
        }
        return "";
    }
}
