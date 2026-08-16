package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.OpenMaidAIChatMessage;
import com.heartfelt.connection.combat.PlayerHarmPenaltyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 伤心女仆拒绝 AI 聊天(v1.5.44)——用户要求:伤心(赌气)状态解除前,
 * 玩家无法通过 Alt+J 进入与她的互动面板。
 *
 * OpenMaidAIChatMessage 是 Alt+J 打开 AI 聊天面板的 C2S 包,服务端 handle
 * 里按 entityId 找女仆并回 SyncMaidAIDataMessage(客户端据此打开 AIChatScreen)。
 * 在服务端 handle HEAD 拦截:目标是伤心窗口内(heartfelt_hurt_until)的女仆
 * → 取消(不打开面板)+ 系统提示"她正在伤心赌气,不想理你"。
 *
 * TLM 类在编译 classpath → 普通 @Mixin。
 */
@Mixin(OpenMaidAIChatMessage.class)
public abstract class HeartbrokenChatGateMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$blockHurtChat(OpenMaidAIChatMessage pkt,
            Supplier<NetworkEvent.Context> supplier, CallbackInfo ci) {
        NetworkEvent.Context ctx = supplier.get();
        if (ctx == null || !ctx.getDirection().getReceptionSide().isServer()) {
            return; // 只服务端拦截(C2S 包正常只会到服务端,防御性判断)
        }
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        Entity e = level.m_6815_(pkt.entityId());
        if (e instanceof EntityMaid maid && PlayerHarmPenaltyManager.isHurtFeeling(maid)) {
            // v1.5.46:女儿伤心时的拒绝提示单独设计(她生爸爸的气)
            boolean child = com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid);
            player.m_213846_(Component.m_237113_(child
                    ? "\u00a77她正在生爸爸的气，不想理你……"
                    : "\u00a77她正在伤心赌气，不想理你……"));
            ci.cancel();
        }
    }
}
