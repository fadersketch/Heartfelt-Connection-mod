package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.quota.ApiQuotaBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Love Loathe 目睹死亡对话配额（v1.5.26）：`EmotionPassiveManager.
 * triggerWitnessDialogue`（女仆目睹死亡触发 LLM 对话）属于女仆主动发起的
 * LLM 调用——纳入全局 API 配额（所有女仆合计 ≤10/天）。
 *
 * @Pseudo + 字符串类名：Love Loathe 可选，未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionPassiveManager")
public abstract class LoveLoatheWitnessQuotaMixin {
    @Inject(method = "triggerWitnessDialogue", at = @At("HEAD"), cancellable = true)
    private static void maidsmart$quotaWitnessDialogue(EntityMaid maid, ServerPlayer player, LivingEntity victim, CallbackInfo ci) {
        if (!ApiQuotaBridge.tryAcquire()) {
            ci.cancel();
        }
    }
}
