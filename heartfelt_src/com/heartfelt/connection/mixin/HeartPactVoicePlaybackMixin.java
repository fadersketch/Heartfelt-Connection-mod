package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.ChildGuardManager;
import com.heartfelt.connection.compat.ReflectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * maidmarriage 心契语音拦截(v1.5.24)——maidmarriage 的「语音包」播放
 * (HeartPactVoicePlayback)是独立音频系统(本地 voice pack 文件 + 语音脚本),
 * 完全不经过 TLM 的 MaidAIChatManager.tts,所以 TTS 拦截对它无效。
 *
 * 根因:灵体(MaidSpiritEntity)与幼年女儿的 maidmarriage 对话(如 HugActionScreen
 * 告白/互动界面)播放心契语音包时走本类统一出口 play(...),TLM TTS 拦截管不到。
 *
 * 修复:在 HeartPactVoicePlayback.play()(private static 唯一统一出口,
 * playFrame/playStructuredLine/replayStructuredLine 全部转发到它)HEAD 拦截——
 * 目标女仆是灵体或幼年女儿(isTooSmall)则取消,不播心契语音;正常女仆不受影响。
 *
 * maidmarriage 类不在编译 classpath:@Pseudo + 字符串 targets + optional 配置
 * (mixins.heartfelt.opt.json,required=false + defaultRequire=0,
 * 目标缺失/方法名变化只警告不崩溃)。
 * 注意:方法体不调用任何 Minecraft SRG 方法(运行时已存在),
 * 只调我们自己的类与 TLM/maidmarriage 的 Mojmap 名方法。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.client.voice.HeartPactVoicePlayback")
public abstract class HeartPactVoicePlaybackMixin {

    /** 签名与 maidmarriage 的 play(String,String,int,int,String,String,String,EntityMaid,boolean) 一致 */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$blockMaidmarriageVoice(String voiceId, String text, int idx, int mode,
            String sourceA, String sourceB, String sourceC, EntityMaid maid, boolean flag,
            CallbackInfo ci) {
        if (maid == null) {
            return;
        }
        // 灵体不播语音(灵魂不该有声音;类不存在即未装 maidmarriage,不拦截)
        Class<?> spirit = ReflectUtil.load("com.example.maidmarriage.entity.MaidSpiritEntity");
        if (spirit != null && spirit.isInstance(maid)) {
            ci.cancel();
            return;
        }
        // v1.5.73:幼年女儿(INFANT/JUVENILE)或伤心窗口(妻子/女儿/恋人被主人打伤
        // 赌气)都不播心契语音包——与 TLM TTS 拦截同规则
        if (ChildGuardManager.isTooSmall(maid)
                || com.heartfelt.connection.dialogue.FamilyInteractionManager.isHurtFeeling(maid)) {
            ci.cancel();
        }
    }
}
