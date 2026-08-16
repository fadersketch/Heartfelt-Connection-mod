package com.heartfelt.connection.mixin;

import com.heartfelt.connection.network.HeartfeltNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 告白/对话静止(v1.4.0 改版;v1.5.17 重构)。
 *
 * v1.4.0:原版 maidmarriage 的 HugActionScreen 显式 isPauseScreen()→false,
 * 本 mixin 强制 true(单机打开对话界面世界暂停)。
 * v1.5.17 修正:全屏暂停会让【主人与告白女仆也停住】——用户明确要求"主人与
 * 对话中的女仆不会停止"。改为:
 * - 不再强制 isPauseScreen=true(去掉全屏暂停,v1.5.5 起暂停已统一走选择性静止);
 * - 告白触发时由服务端 DialogueFreezeManager.startFreeze 做选择性静止
 *   (其他生物定住、主人与告白女仆可动);
 * - 本 mixin 在界面关闭时发 ChatFreezePacket(false) 通知服务端恢复。
 */
@Mixin(targets = "com.example.maidmarriage.client.HugActionScreen")
public abstract class HugActionScreenPauseMixin {

    /**
     * v1.5.17:关闭告白/互动界面 → 服务端恢复选择性静止。
     * m_7379_(onClose) 可能继承自 Screen,加 require=0 防目标解析失败崩溃
     * (与 AIChatScreenDramatizeMixin 同款)。
     */
    @Inject(method = "m_7379_", at = @At("HEAD"), require = 0)
    private void heartfelt$unfreezeOnClose(CallbackInfo ci) {
        try {
            // 从 maidmarriage 的 HugClientState 取当前互动女仆 UUID(反射,无硬依赖)
            Class<?> cls = Class.forName("com.example.maidmarriage.client.HugClientState");
            Object uuid = cls.getMethod("getLocalInteractionMaidUuid").invoke(null);
            if (uuid instanceof java.util.UUID u) {
                HeartfeltNetwork.channel().sendToServer(
                        new HeartfeltNetwork.ChatFreezePacket(u, false));
            }
        } catch (Exception ignored) {
        }
    }
}
