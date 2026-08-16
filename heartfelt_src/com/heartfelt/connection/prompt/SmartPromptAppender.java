package com.heartfelt.connection.prompt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.relationship.RelationshipExemption;

/**
 * 关系准则提示词(v1.0.0;v1.1.0 重构:文案迁入 PromptTexts,修 A12 档位区间)。
 *
 * 背景:TLM 的 StringConstant.FULL_SETTING 是编译期常量,javac 已把它内联进
 * PapiReplacer.replaceSetting 方法体——改 StringConstant 类在运行时【从未生效】。
 * 正确做法:用 Mixin 拦截 LLMMessage.systemChat,在 system 提示词末尾
 * 【运行时追加】本准则段(见 SmartPromptMixin)。
 *
 * 段落是"条件式"的:<context> 中每次对话都会注入 relationship 状态栏
 * (RelationshipMaidContext)与 favorability 数值——LLM 看到 relationship 标签
 * 就按关系专属准则演(替换 favorability 量表);没有标签就按 4 级量表演。
 *
 * v1.1.0(A12):好感档位在 PromptTexts.FAVORABILITY_SCALE 中改为明确区间
 * (0-63 / 64-191 / 192-383 / 384),避免 LLM 按 points/128 线性误判档位。
 * v1.3.0:追加事件历史(P1)与情境变量(P2)段。
 */
public final class SmartPromptAppender {
    /** 准则段标识(幂等检测用):段落已注入时不重复追加 */
    public static final String MARKER = PromptTexts.MARKER;

    private SmartPromptAppender() {
    }

    /** 根据女仆关系状态生成追加段落;无关系时返回 favorability 量表段 */
    public static String build(EntityMaid maid) {
        String key = RelationshipExemption.strictRelationKey(maid);
        String base;
        if (key != null) {
            base = PromptTexts.RELATIONSHIP_GUIDANCE.formatted(relationSection(maid, key));
        } else {
            base = PromptTexts.FAVORABILITY_SCALE;
        }
        // v1.3.0:P1 事件历史(我们一起经历的事)
        String history = com.heartfelt.connection.memory.EventHistoryManager.buildHistoryText(maid);
        if (!history.isEmpty()) {
            base += "\n## Shared History (Heartfelt-connection)\n" + history;
        }
        // v1.3.0:P2 情境变量(饥饿/地点/主人状态)
        String situation = com.heartfelt.connection.prompt.SituationalPrompt.build(maid);
        if (!situation.isEmpty()) {
            base += "\n" + situation;
        }
        // v1.3.0:P3 后果文本弧(拒绝后/破裂后/和好后的相处模式)
        String aftermath = com.heartfelt.connection.prompt.AftermathPrompt.build(maid);
        if (!aftermath.isEmpty()) {
            base += "\n" + aftermath;
        }
        // v1.5.98:P4 连续情感快照(情绪/情感阶段/亲密/冲突/债/思慕 + 表达建议)
        // ——从现有事件喂脉冲演算(被打/喂食/亲密/告白/破裂/哀悼),注入让 LLM
        // 的对话语气贴合"她此刻的情绪",而不是每次只看当下好感。
        String affect = com.heartfelt.connection.affect.AffectStateManager.buildPromptBlock(maid);
        if (!affect.isEmpty()) {
            base += "\n" + affect;
        }
        return base;
    }

    /** v1.2.2:女儿准则按成长阶段细分(婴儿/幼儿/少女/成年)
     *  v1.5.106:INFANT(婴儿,旁白无台词)与 JUVENILE(幼儿,简单句子)拆开。 */
    private static String relationSection(EntityMaid maid, String key) {
        return switch (key) {
            case "wife" -> PromptTexts.WIFE_SECTION;
            case "daughter" -> {
                MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
                if (stage == MaidMarriageCompat.ChildStage.ADULT) {
                    yield PromptTexts.DAUGHTER_ADULT_SECTION;
                }
                if (stage == MaidMarriageCompat.ChildStage.CHILD) {
                    yield PromptTexts.DAUGHTER_CHILD_SECTION;
                }
                if (stage == MaidMarriageCompat.ChildStage.JUVENILE) {
                    yield PromptTexts.DAUGHTER_JUVENILE_SECTION;
                }
                yield PromptTexts.DAUGHTER_INFANT_SECTION;
            }
            default -> PromptTexts.LOVER_SECTION;
        };
    }
}
