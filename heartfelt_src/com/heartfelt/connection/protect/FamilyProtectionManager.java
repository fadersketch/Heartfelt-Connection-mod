package com.heartfelt.connection.protect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.combat.AttackBetrayerBehavior;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 家庭保护(v1.2;v1.5.26 扩展;v1.1.0 A5 远程保护 + A3 UUID 比较)。
 *
 * 拦截规则(与好感仲裁轴一致,纯启发式、零依赖):
 * - 攻击者与受害者都是 EntityMaid
 * - 受害者是**关系女仆**(恋人/妻子/女儿,RelationshipExemption)→ 无条件豁免
 *   (背叛女仆除外:其攻击不该被归零,否则背叛仇恨优先级形同虚设)
 * - 攻击者好感 >= 64 且两者同主人 → 取消伤害(深爱女仆不打同主人女仆)
 *
 * v1.1.0(A5):同时检查【间接来源】`source.getEntity()`——箭/弹幕/枪械的
 * 直接实体是弹道,间接实体才是射手;旧版只查直接实体,远程伤害穿透保护。
 * (近战路径由 EntityMaidDoHurtProtectMixin 兜底——callresponse 会用中性伤害源
 * 重放近战,LivingHurtEvent 里直接实体恒为 null。)
 */
public class FamilyProtectionManager {

    @SubscribeEvent
    public void onMaidHurtMaid(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid victim)) {
            return;
        }
        // A5:间接来源(弹道射手)优先,退回直接来源
        // m_7640_ = getEntity(责任实体,如箭的射手);m_7639_ = getDirectEntity(弹道本身)
        Entity indirect = source.m_7640_();
        Entity direct = source.m_7639_();
        if (!(indirect instanceof EntityMaid) && !(direct instanceof EntityMaid)) {
            return;
        }
        EntityMaid attacker = indirect instanceof EntityMaid m ? m
                : direct instanceof EntityMaid m2 ? m2 : null;
        if (attacker == null || attacker == victim) {
            return;
        }
        // 受害者是自身冻结层女仆(恋人/妻子/女儿/深爱)→ 无条件豁免;
        // 背叛女仆豁免——其攻击不该被家庭保护归零(仇恨优先级:主人>妻子/女儿>普通)
        if (RelationshipExemption.isFrozen(victim)) {
            if (!AttackBetrayerBehavior.isBetraying(attacker)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!attacker.m_21824_() || !victim.m_21824_()) {
            return;
        }
        // A3:UUID 比较代替引用比较
        if (!DialogueDispatcher.sameOwner(attacker, victim)) {
            return;
        }
        // 深爱(好感 >=64)的女仆不打同主人的女仆——防止母女/家人互殴
        if (attacker.getFavorability() >= 64) {
            event.setCanceled(true);
        }
    }
}
