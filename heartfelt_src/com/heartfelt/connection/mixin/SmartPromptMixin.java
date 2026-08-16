package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.prompt.SmartPromptAppender;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 提示词运行时注入（v1.0.0）：拦截 TLM 的 `LLMMessage.systemChat(EntityMaid, String)`
 * ——它是 MaidAIChatManager.getMessages 两条设定路径（自定义设定 PapiReplacer /
 * 默认 YAML Setting）的唯一汇聚点（buildMessage 只调用它一次）。
 *
 * 在 system 提示词末尾追加关系准则段（SmartPromptAppender）——
 * 无确认关系 → favorability 4 级量表；有确认关系 → 关系专属准则。
 *
 * v1.0.1：修复注入方式——旧版 @ModifyArg(at=@At("HEAD")) 是非法组合（@ModifyArg
 * 必须指向方法调用指令，HEAD 是方法开头不是调用）→ 女仆一触发对话、LLMMessage
 * 类被加载即 InvalidInjectionException 崩服。改为 MixinExtras @WrapMethod 包裹
 * 整个静态方法：直接拿到 (maid, setting)，追加后 original.call 原样调用，
 * 与 jar 字节码具体内容无关，对原版/SMART 都稳健。
 *
 * TLM 类未混淆（开发名 systemChat），编译 cp 上有 LLMMessage → 普通 @Mixin。
 */
@Mixin(LLMMessage.class)
public abstract class SmartPromptMixin {

    @WrapMethod(method = "systemChat")
    private static LLMMessage heartfelt$wrapSystemChat(EntityMaid maid, String setting,
            Operation<LLMMessage> original) {
        String guidance = SmartPromptAppender.build(maid);
        // 幂等保护：setting 已含准则段（历史消息重建等场景）时不重复追加
        if (guidance.isEmpty() || setting.contains(SmartPromptAppender.MARKER)) {
            return original.call(maid, setting);
        }
        return original.call(maid, setting + "\n\n" + guidance);
    }
}
