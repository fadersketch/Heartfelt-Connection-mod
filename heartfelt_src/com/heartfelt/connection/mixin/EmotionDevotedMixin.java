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
 * 自动忠诚(v1.2.1,用户确认的关系模型)。
 *
 * 确认关系(恋人/妻子/女儿 = 冻结层)后,信任/恐惧数值冻结且不再作为任何依据——
 * 爱憎分明的 4 条关系线(忠诚/溺爱/淡忘/背叛)中,给玩家的加成【自动判定为忠诚】:
 * 拦截 `EmotionDevotedManager.isDevoted`(HEAD)——冻结层女仆一律返回 true,获得:
 * - 属性加成:攻击 +5、移动速度 +0.1(Devoted buff)
 * - 自动攻击附近背叛女仆(16 格,60% 概率从叛徒身上扒装备——扒的是叛徒不是主人)
 * - 主人血量 <10% 时自动回血(HEAL_COOLDOWN 1200 tick)
 *
 * 原版 isDevoted 要求 信任≥80 且 恐惧≥80——确认关系后数值冻结,未必到线;
 * 本混入让"忠诚"与冻结数值解耦,直接以关系状态为准。
 *
 * @Pseudo + 字符串类名:Love Loathe 可选,未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionDevotedManager")
public abstract class EmotionDevotedMixin {

    @Inject(method = "isDevoted", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$autoLoyalForFrozen(EntityMaid maid, ServerPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (RelationshipExemption.isFrozen(maid)) {
            cir.setReturnValue(true);
        }
    }
}
