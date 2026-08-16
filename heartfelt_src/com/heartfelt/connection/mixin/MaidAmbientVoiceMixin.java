package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.ChildGuardManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 婴儿/幼儿禁自主语音(v1.5.64)——TLM 女仆"声音频率"(global_maid_sound_frequency)
 * 驱动的自主讲话声(ambient,声音包语音)走 EntityMaid.getAmbientSound(m_7515_):
 * - 不经 MaidAIChatManager.tts(AI 聊天语音,v1.5.2/1.5.21 已拦);
 * - 不经 maidmarriage 心契语音 HeartPactVoicePlayback(v1.5.24 已拦);
 * ——婴儿/幼儿仍会周期性播放语音包,这是最后的缺口。
 *
 * 修复:包裹 getAmbientSound 返回 null(原版 playAmbientSound 对 null 有安全
 * 检查,直接不播放)——等效于把声音频率调成 0;捡拾声 tryPlayMaidPickupSound
 * 一并拦截。婴儿/幼儿完全不发出声音包语音,文本对话/气泡不受影响。
 *
 * TLM 类在编译 classpath(original_tlm.jar)→ 普通 @Mixin,注册于
 * mixins.heartfelt.json core 段(服务端/客户端共用)。
 */
@Mixin(EntityMaid.class)
public abstract class MaidAmbientVoiceMixin {

    @WrapMethod(method = "m_7515_")
    private SoundEvent heartfelt$noAmbientVoice(Operation<SoundEvent> original) {
        EntityMaid self = (EntityMaid) (Object) this;
        // v1.5.73:婴儿/幼儿 或 伤心窗口(妻子/女儿/恋人被主人打伤赌气)——都不播
        // 自主语音包(声音频率 = 0);伤心窗口内她坐着赌气,不该发出声音包语音
        if (ChildGuardManager.isTooSmall(self)
                || com.heartfelt.connection.dialogue.FamilyInteractionManager.isHurtFeeling(self)) {
            return null;
        }
        return original.call();
    }

    @WrapMethod(method = "tryPlayMaidPickupSound")
    private void heartfelt$noPickupSound(Operation<Void> original) {
        EntityMaid self = (EntityMaid) (Object) this;
        if (!ChildGuardManager.isTooSmall(self)
                && !com.heartfelt.connection.dialogue.FamilyInteractionManager.isHurtFeeling(self)) {
            original.call();
        }
    }
}
