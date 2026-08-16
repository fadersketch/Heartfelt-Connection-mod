package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 爱憎分明 × 心契誓约隔离——淡忘（v1.5.26）：关系女仆（恋人/妻子/女儿）
 * **永不淡忘**（不会"忘掉主人"传送走/解除契约）——拦截
 * EmotionForgettingManager.triggerForgetting（HEAD cancel）。
 *
 * @Pseudo + 字符串类名：Love Loathe 可选，未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionForgettingManager")
public abstract class EmotionForgettingIsolationMixin {
    @Inject(method = "triggerForgetting", at = @At("HEAD"), cancellable = true)
    private void maidsmart$noForgettingForRelated(EntityMaid maid, ServerPlayer player, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
        }
    }
}
