package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.combat.PlayerHarmPenaltyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家攻击女仆记录(v1.5.9,惩罚兼容)——配合 CallResponseHurtLimitMixin。
 *
 * callresponse(爱憎分明)在主人攻击时会用中性源(DamageSources.generic())
 * 重放伤害并取消原 hurt,导致后续 LivingHurtEvent 的伤害源是 generic()
 * (directEntity=null),PlayerHarmPenaltyManager 的"仅玩家来源"判定失效,
 * 伤害惩罚永不触发。
 *
 * 本 mixin 在 EntityMaid.hurt(HEAD)记录"玩家攻击女仆"(maid→player+tick),
 * PlayerHarmPenaltyManager 在 generic 源事件里查记录还原玩家身份。
 */
@Mixin(EntityMaid.class)
public abstract class EntityMaidHurtRecordMixin {

    @Inject(method = "m_6469_", at = @At("HEAD"))
    private void heartfelt$recordPlayerAttack(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (source == null) {
            return;
        }
        // v1.5.67:近战(directEntity)与远程/重放(getEntity 造成者)都记录——
        // 旧版只查 m_7640_(directEntity),远程弓/弩在 hurt 入口记录不上,
        // 一旦 LivingHurtEvent 链断(其他 mod 取消/时序),幼儿好感度惩罚就丢
        Entity direct = source.m_7640_();
        if (direct instanceof ServerPlayer player) {
            EntityMaid maid = (EntityMaid) (Object) this;
            PlayerHarmPenaltyManager.recordPlayerAttack(maid, player,
                    maid.m_9236_().m_46467_());
            return;
        }
        Entity cause = source.m_7639_();
        if (cause instanceof ServerPlayer player) {
            EntityMaid maid = (EntityMaid) (Object) this;
            PlayerHarmPenaltyManager.recordPlayerAttack(maid, player,
                    maid.m_9236_().m_46467_());
        }
    }
}
