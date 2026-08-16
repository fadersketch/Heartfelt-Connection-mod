package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.relationship.FreezeConversion;
import com.heartfelt.connection.relationship.RelationshipExemption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 关系恐惧豁免(v1.5.26;v1.2.0 冻结语义升级)。
 *
 * 痛点:妻子/女儿/恋人明明已经确定关系,Love Loathe 却还在产生恐惧——恐惧
 * 对话/背叛/逃跑照常触发,违和感极重。
 *
 * 做法:拦截 Love Loathe 恐惧值的写入入口 `EmotionData.addFear / addFearFloat`
 * (HEAD cancel)——自身冻结层成立时:
 * - 恐惧数值【完全冻结】:正向(涨恐惧)和负向(降恐惧)都不写入
 * - v1.2.0:玩家造成的恐惧变化【等比折算为好感变化】(FreezeConversion:
 *   恐惧+N→好感-N×ratio,恐惧-N→好感+N×ratio;扣减方向受每日上限)
 * - 非玩家来源(怪物/环境/自然衰减):只冻结、不折算
 *
 * 与 EmotionTrustExemptionMixin 配对:恐惧冻结 + 信任冻结 = 完全冻结。
 *
 * @Pseudo + 字符串类名:Love Loathe 可选,未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionData")
public abstract class EmotionFearExemptionMixin {

    /** addFear(EntityMaid, UUID, int):冻结层下恐惧完全不移动 */
    @Inject(method = "addFear", at = @At("HEAD"), cancellable = true)
    private static void maidsmart$exemptFear(EntityMaid maid, UUID sourceUuid, int amount, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
            FreezeConversion.convertFear(maid, sourceUuid, amount);
        }
    }

    /** addFearFloat(EntityMaid, UUID, float):浮点路径兜底 */
    @Inject(method = "addFearFloat", at = @At("HEAD"), cancellable = true)
    private static void maidsmart$exemptFearFloat(EntityMaid maid, UUID sourceUuid, float amount, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
            FreezeConversion.convertFear(maid, sourceUuid, amount);
        }
    }
}
