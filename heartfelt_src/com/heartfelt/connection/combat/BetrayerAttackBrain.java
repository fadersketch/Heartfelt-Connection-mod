package com.heartfelt.connection.combat;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

/**
 * 反击背叛女仆的 ExtraMaidBrain(v1.1.0 重构:原匿名类提为命名类)。
 *
 * 优先级来自配置(默认 150)——低于自保 250、高于 TLM core 行为最高 99;
 * 同一 RunningOne 模块内同优先级会互抢槽位,注意与 promaid 行为错开。
 */
public final class BetrayerAttackBrain implements IExtraMaidBrain {
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
        return List.of(Pair.of(HeartfeltConfig.BETRAYER_BRAIN_PRIORITY.get(),
                new AttackBetrayerBehavior()));
    }
}
