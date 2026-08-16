package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.ReflectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灵体语音修复(v1.5.2)——maidmarriage 原版 bug:小女仆变成灵体
 * (MaidSpiritEntity extends EntityMaid)后,TLM 的 TTS 语音包仍然可以播放。
 *
 * 根因:灵体是 instanceof EntityMaid 为真的女仆,TLM 的语音入口
 * (MaidAIChatManager.tts,远程 TTS 的 TTSAudioToClientMessage 与系统 TTS 的
 * TTSSystemAudioToClientMessage 都由它发起)对它全部放行。
 *
 * 修复:在服务端 tts() 入口拦截——目标女仆是灵体则直接取消,
 * 一个点覆盖远程 + 系统全部 TTS 路径;灵体的 LLM 文本对话不受影响。
 * 灵体判定用反射(maidmarriage 类不在编译 classpath)。
 */
@Mixin(MaidAIChatManager.class)
public abstract class MaidSpiritTtsGuardMixin {

    @Inject(method = "tts", at = @At("HEAD"), cancellable = true)
    private void heartfelt$blockSpiritTts(TTSSite site, String chatText, String ttsText,
            long waitingChatBubbleId, CallbackInfo ci) {
        MaidAIChatManager self = (MaidAIChatManager) (Object) this;
        EntityMaid maid = self.getMaid();
        if (maid == null) {
            return;
        }
        // 灵体不播放语音(灵魂不该有 TTS);类不存在(未装 maidmarriage)不拦截
        Class<?> spirit = ReflectUtil.load("com.example.maidmarriage.entity.MaidSpiritEntity");
        if (spirit != null && spirit.isInstance(maid)) {
            ci.cancel();
            return;
        }
        // v1.5.21:太小的女儿(INFANT/JUVENILE)也不播语音包(太小的女仆不该开口说话);
        // v1.5.73:伤心窗口(被主人打伤赌气)也不播——妻子/女儿/恋人一致;
        // LLM 文本对话不受影响
        if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)
                || com.heartfelt.connection.dialogue.FamilyInteractionManager.isHurtFeeling(maid)) {
            ci.cancel();
        }
    }
}
