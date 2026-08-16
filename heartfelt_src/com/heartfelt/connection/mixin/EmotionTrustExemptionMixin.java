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
 * 关系信任冻结(v1.0.0;v1.2.0 冻结语义升级)。
 *
 * 拦截 Love Loathe 信任值的写入入口 `EmotionData.addTrust / addTrustFloat`
 * (HEAD cancel)——自身冻结层(结婚/恋人且好感≥128/好感≥192深爱/父女)成立时:
 * - 信任数值【完全冻结】:任何方向的信任变化都不写入(玩家造成的也不写)
 * - v1.2.0:玩家造成的信任变化【等比折算为好感变化】(FreezeConversion:
 *   信任+N→好感+N×ratio,信任-N→好感-N×ratio,受每日上限)
 * - 非玩家来源的变化:只冻结、不折算
 *
 * 与 EmotionFearExemptionMixin 配对:恐惧冻结 + 信任冻结 = 完全冻结。
 *
 * @Pseudo + 字符串类名:Love Loathe 可选,未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionData")
public abstract class EmotionTrustExemptionMixin {

    /** addTrust(EntityMaid, UUID, int):冻结层下信任完全不移动 */
    @Inject(method = "addTrust", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$exemptTrust(EntityMaid maid, UUID sourceUuid, int amount, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
            FreezeConversion.convertTrust(maid, sourceUuid, amount);
        }
    }

    /** addTrustFloat(EntityMaid, UUID, float):浮点路径兜底 */
    @Inject(method = "addTrustFloat", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$exemptTrustFloat(EntityMaid maid, UUID sourceUuid, float amount, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
            FreezeConversion.convertTrust(maid, sourceUuid, amount);
        }
    }
}
