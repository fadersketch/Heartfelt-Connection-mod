package com.heartfelt.connection.memory;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.relationship.RelationshipExemption;

/**
 * 关系栏（v1.0.0）：把确认关系（妻子/恋人/女儿）作为结构化状态注入
 * AI 对话的 <context>——每次对话 LLM 明确感知当前关系。
 *
 * 输出 `relationship: wife / lover / daughter`；无确认关系返回空串
 * （GameContextRegister 的 isNotBlank 过滤自动隐藏该行）→ 走原 favorability 流程。
 *
 * 与 memories 标签互补：memories 是叙述性事实（"我已与主人结为夫妻"），
 * relationship 是明确的状态栏（提示词按它切换关系专属准则）。
 * 高好感（≥192）未告白不算确认关系 → 关系栏不显示（暗恋不是恋人）。
 */
public class RelationshipMaidContext extends AbstractMaidContext {

    public RelationshipMaidContext() {
        super("relationship", "Relationship status");
    }

    @Override
    public String getValue(EntityMaid maid) {
        String key = RelationshipExemption.strictRelationKey(maid);
        return key == null ? "" : key;
    }
}
