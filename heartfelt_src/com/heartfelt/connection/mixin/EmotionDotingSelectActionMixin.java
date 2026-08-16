package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 爱憎分明 × 心契誓约隔离——溺爱动作（v1.1.0）。
 *
 * 痛点：EmotionDotingManager.selectAction 只认 callresponse 自己的
 * isPartner（已婚 或 好感≥192）→ 送花；否则 40% 偷主人 / 30% 翻箱子。
 * 而 heartfelt 已把"告白完成"（好感 128~191）视为确认关系（恋人）——
 * 恋人/女儿在溺爱状态会偷主人东西、翻主人箱子，严重违和。
 *
 * 做法：拦截 selectAction（HEAD）——冻结层女仆（恋人/妻子/女儿/好感≥192
 * 深爱）一律返回 FLOWER（送花），跳过偷窃/翻箱分支。原版"被宠坏女仆偷东西"
 * 只保留给未确认关系的普通溺爱女仆。
 *
 * @Pseudo + 字符串类名：Love Loathe 可选，未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager")
public abstract class EmotionDotingSelectActionMixin {

    @Inject(method = "selectAction", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$flowerForFrozen(EntityMaid maid, ServerPlayer player, CallbackInfoReturnable cir) {
        if (!RelationshipExemption.isFrozen(maid)) {
            return;
        }
        Object flower = com.heartfelt.connection.compat.CallResponseCompat.actionTypeFlower();
        if (flower != null) {
            cir.setReturnValue(flower);
        }
    }
}
