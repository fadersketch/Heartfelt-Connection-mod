package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.combat.AttackBetrayerBehavior;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 家庭保护真正生效层（v1.1.0）。
 *
 * 背景：FamilyProtectionManager 走 LivingHurtEvent，但爱憎分明（callresponse）
 * 的 MixinEntityMaid 在 `hurt(HEAD)` 把所有"女仆打女仆"近战用【中性伤害源】
 * 重放（super.m_6469_ + setReturnValue）——LivingHurtEvent 里直接实体恒为 null，
 * 家庭保护从未触发（死代码）。
 *
 * 做法：拦截 TLM EntityMaid 覆写的 `m_7327_`（doHurtTarget）——这是所有
 * 女仆近战（TLM 战斗任务 / 背叛 / 死忠讨伐 / 决斗）的唯一汇聚点，且发生在
 * callresponse 的 hurt 重放【之前】：
 * - 攻击者是背叛女仆 → 放行（背叛仇恨设计：专挑主人珍视的人下手）
 * - 受害者是关系女仆（恋人/妻子/女儿）→ 取消本次攻击
 * - 同主人且攻击者好感 ≥64 → 取消（深爱女仆不打同主人女仆，防母女/家人互殴）
 *
 * 目标 TLM EntityMaid（在编译 classpath）→ 普通 @Mixin。
 */
@Mixin(EntityMaid.class)
public abstract class EntityMaidDoHurtProtectMixin {

    @Inject(method = "m_7327_", at = @At("HEAD"), cancellable = true)
    private void heartfelt$protectFamily(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof EntityMaid victim)) {
            return;
        }
        EntityMaid attacker = (EntityMaid) (Object) this;
        if (attacker == victim) {
            return;
        }
        // v1.5.48:女儿永不伤害爸爸——MC 战斗逻辑(范围伤害/重放/自动寻敌)下
        // 女儿可能打到/打死主人,绝对拦截(与背叛清除双保险)
        if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(attacker)
                && attacker.m_269323_() == target) {
            cir.setReturnValue(false);
            return;
        }
        // 背叛女仆豁免——背叛的目标选择已优先关系女仆（主人>妻子/女儿>普通），
        // 其攻击不该被家庭保护拦下，否则背叛仇恨优先级形同虚设
        if (AttackBetrayerBehavior.isBetraying(attacker)) {
            return;
        }
        // 受害者是关系女仆 → 正常女仆不会把主人的另一半/女儿当攻击对象
        if (RelationshipExemption.isFrozen(victim)) {
            cir.setReturnValue(false);
            return;
        }
        if (!attacker.m_21824_() || !victim.m_21824_()) {
            return;
        }
        // A3:UUID 比较代替引用比较(跨维度/重进不再失效)
        if (!com.heartfelt.connection.dialogue.DialogueDispatcher.sameOwner(attacker, victim)) {
            return;
        }
        // 深爱(好感 >=64)的女仆不打同主人的女仆——防止母女/家人互殴
        if (attacker.getFavorability() >= 64) {
            cir.setReturnValue(false);
        }
    }
}
