package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.quota.ApiQuotaBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Love Loathe 主动对话配额（v1.5.26）：`EmotionActiveDialogue.triggerAIDialogue`
 * 是 Love Loathe 的"主动表达情绪"对话入口（周期扫描 + 交互触发）——属于女仆
 * 主动发起的 LLM 调用，纳入全局 API 配额（所有女仆合计 ≤10/天）。
 * 配额满 → 取消本次调用（不发起 LLM，静默跳过）。
 *
 * @Pseudo + 字符串类名：Love Loathe 可选，未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionActiveDialogue")
public abstract class LoveLoatheActiveQuotaMixin {
    @Inject(method = "triggerAIDialogue", at = @At("HEAD"), cancellable = true)
    private static void maidsmart$quotaActiveDialogue(EntityMaid maid, ServerPlayer player, String prompt, CallbackInfo ci) {
        if (!ApiQuotaBridge.tryAcquire()) {
            ci.cancel();
        }
    }
}
