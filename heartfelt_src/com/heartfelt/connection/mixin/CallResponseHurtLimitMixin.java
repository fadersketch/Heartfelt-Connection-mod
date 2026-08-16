package com.heartfelt.connection.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 修复"主人攻击女仆限伤失效"(v1.5.9)——根因不是 Better Combat:
 * callresponse(爱憎分明)的 `MixinEntityMaid.example$onHurtHead` 在主人攻击时,
 * 用 `DamageSources.generic()` 中性源重放伤害并取消原 hurt——导致:
 * - TLM 原版限伤(主人伤害 ÷5 封顶 2)被跳过;
 * - promaid 1% 上限(@Redirect Mth.clamp,在 EntityMaid.hurt 内)被跳过;
 * - heartfelt 伤害惩罚(LivingHurtEvent 要求玩家源)永不触发。
 *
 * 修复:ModifyVariable 修改 example$onHurtHead 的 amount 参数(index=2,
 * argsOnly)——主人攻击时把伤害改为 TLM 原版语义(÷5 封顶 2),
 * callresponse 重放的就是限伤后的值。仅对"主人玩家"生效(与 TLM ÷5 一致);
 * 非主人攻击不重放,不受影响。
 *
 * @Pseudo + 字符串 target:callresponse 可选依赖(实际是必装,但类不在编译
 * classpath);require=0:callresponse 升级改方法名时静默跳过,不崩服。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.mixin.MixinEntityMaid")
public abstract class CallResponseHurtLimitMixin {

    @ModifyVariable(method = "example$onHurtHead", at = @At("HEAD"),
            index = 2, argsOnly = true, require = 0)
    private float heartfelt$limitOwnerDamage(DamageSource source, float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        // 目标类(callresponse 的 MixinEntityMaid)注入在 EntityMaid 上,this 必为女仆
        com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid =
                (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) (Object) this;
        net.minecraft.world.entity.Entity direct = source.m_7640_();
        if (direct instanceof Player player && maid.m_21830_(player)) {
            // v1.5.10:空手攻击不削(空手 1.0 本身低伤害,且会被护甲吸收到无反馈
            // ——恢复"空手能打女仆"的原版手感);武器/其他伤害保持 TLM ÷5 封顶 2
            if (player.m_21205_().m_41619_()) {
                return amount;
            }
            // TLM 原版语义:主人伤害 ÷5 封顶 2(callresponse 重放绕过了它)
            return Math.min(amount / 5.0f, 2.0f);
        }
        return amount;
    }
}
