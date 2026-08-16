package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 心契誓约 × 坐姿工作（v1.5.82）：子代女仆（女儿）坐着也能安排工作。
 *
 * 背景：maidmarriage 的 MaidWorkManager.resolveWorkBlockReason 检查到坐姿
 * （SITTING）时返回 SITTING → 调用方发提示
 * "message.maidmarriage.child.work.sitting"（"%s现在坐着休息呢，让她站起来
 * 后再安排%s吧。"）并拒绝安排——与"女仆坐着干活"（Promaid 支持的坐姿工作）
 * 冲突：女儿坐姿时无法安排工作学习。
 *
 * 做法：拦截 resolveWorkBlockReason（private static，RETURN 改写）——返回
 * SITTING 时改为 NONE（可工作），坐姿女仆照常安排任务；睡觉（SLEEPING）等
 * 其他原因保持原样。目标类在 maidmarriage（不在编译 classpath）→ @Pseudo +
 * 字符串类名；handler 用原始 CallbackInfoReturnable 避免泛型类加载。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.compat.MaidWorkManager")
public abstract class MaidWorkSittingMixin {

    @Inject(method = "resolveWorkBlockReason", at = @At("RETURN"), cancellable = true)
    private static void heartfelt$allowSittingWork(EntityMaid maid, CallbackInfoReturnable cir) {
        try {
            Object reason = cir.getReturnValue();
            // v1.5.70:婴儿/幼儿(INFANT/JUVENILE)不能安排任何工作——无论是否坐着。
            // 旧 SITTING→NONE 放行对幼儿是反效果:婴儿被强制坐下却因放行可工作、
            // 幼儿不坐下直接可工作("任何工作什么的都做不了"漏网)。强制返回 SITTING
            // 阻断——调用方发 message.maidmarriage.child.work.sitting,文案由 heartfelt
            // lang 覆盖为"还太小了,还不能安排工作"(只有幼儿会看到这条:其余女儿的
            // 坐姿在下方被转 NONE 放行,不触发该消息)。
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
                if ("NONE".equals(reason.toString())) {
                    Object sitting = java.lang.Enum.valueOf(
                            (Class<? extends java.lang.Enum>) reason.getClass(), "SITTING");
                    cir.setReturnValue(sitting);
                }
                return;
            }
            // 其余女儿(少女/成女):坐姿→NONE 允许工作(与"女仆坐着干活"兼容)
            if (reason != null && "SITTING".equals(reason.toString())) {
                // 改为 NONE（可工作）——调用方 reason != NONE 才发提示并拒绝
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object none = java.lang.Enum.valueOf((Class<? extends java.lang.Enum>) reason.getClass(), "NONE");
                cir.setReturnValue(none);
            }
        } catch (Exception ignored) {
            // 枚举/类异常静默——保持原行为
        }
    }
}
