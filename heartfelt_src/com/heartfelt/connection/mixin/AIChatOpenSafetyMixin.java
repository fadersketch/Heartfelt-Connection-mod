package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.OpenMaidAIChatMessage;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.prompt.PromptTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alt+J 对话面板安全区(v1.5.8)——原版缺失:玩家按 Alt+J 打开 LLM 对话面板
 * 前,必须确认玩家与目标女仆周围一定范围内没有敌人(至少先把敌人打掉),
 * 否则不打开面板并提示。
 *
 * 拦截 TLM `OpenMaidAIChatMessage` 服务端私有 handle(在 enqueueWork 内,
 * 服务端主线程),检查不通过 → 取消同步(客户端不会打开 AIChatScreen)
 * + 系统消息提示。
 */
@Mixin(OpenMaidAIChatMessage.class)
public abstract class AIChatOpenSafetyMixin {

    @Inject(method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/ai/OpenMaidAIChatMessage;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void heartfelt$blockUnsafeChat(OpenMaidAIChatMessage message,
            ServerPlayer player, CallbackInfo ci) {
        if (player == null) {
            return;
        }
        Entity entity = player.m_9236_().m_6815_(message.entityId());
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        double radius = HeartfeltConfig.DIALOGUE_SAFE_RADIUS.get();
        if (!DialogueDispatcher.isSafeArea(player, maid, radius)) {
            // 附近有敌人:不打开对话面板,先打掉敌人再说
            player.m_213846_(Component.m_237113_(PromptTexts.dialogueBlockedByHostiles()));
            ci.cancel();
        }
    }
}
