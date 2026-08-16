package com.heartfelt.connection.memory;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 剧情记忆栏（v1.0.0）：把 maidmarriage 的既定剧情状态（结婚/怀孕/告白/父女/丧子）
 * 作为独立 context 注入 <context>，与 Promaid 的基础记忆（喂食/好感/死亡）解耦。
 *
 * 数据来自 StoryMemoryManager（软依赖 maidmarriage，实时读取）；
 * 无剧情时返回空串（自动过滤，不占用 token）。
 */
public class StoryMaidContext extends AbstractMaidContext {

    public StoryMaidContext() {
        super("story", "Story memories");
    }

    @Override
    public String getValue(EntityMaid maid) {
        return StoryMemoryManager.buildStoryMemory(maid);
    }
}
