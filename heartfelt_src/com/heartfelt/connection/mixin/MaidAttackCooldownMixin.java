package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 空手/方块攻击女仆修复(v1.5.11)——Better Combat 挥击后把
 * Minecraft.missTime(f_91078_) 设为完整攻击冷却,而 doAttack 开头
 * `if (missTime > 0) return false` 会拒绝所有原版攻击路径(空手/方块),
 * 表现为"连打都打不到";BC 剑走自己的 C2S 攻击路径不受影响。
 *
 * 修复:目标实体是女仆时清零 missTime——空手/方块可以正常攻击女仆
 * (配合 CallResponseHurtLimitMixin:空手豁免 ÷5,恢复原版手感)。
 * 只对女仆生效,其他生物仍遵循 BC 的攻击节奏。
 *
 * 客户端专用 → mixins.heartfelt.json 的 client 段。
 */
@Mixin(Minecraft.class)
public abstract class MaidAttackCooldownMixin {
    /** Minecraft.missTime(攻击冷却;doAttack 开头 >0 直接 return false) */
    @Shadow
    public int f_91078_;

    @Inject(method = "m_202354_", at = @At("HEAD"))
    private void heartfelt$allowMaidAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (this.f_91078_ > 0 && mc.f_91077_ instanceof EntityHitResult hit
                && hit.m_82443_() instanceof EntityMaid) {
            // Better Combat 挥击冷却会拦掉对女仆的原版攻击——清零豁免
            this.f_91078_ = 0;
        }
    }
}
