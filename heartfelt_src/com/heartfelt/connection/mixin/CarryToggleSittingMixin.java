package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "让妈妈抱"坐姿放宽(v1.5.40)——用户要求:让妈妈抱的键在坐下/站起时都能触发。
 *
 * maidmarriage 原版 MaidCarryChildManager 的三处 isMaidInSittingPose 检查阻止了
 * 坐下状态抱起:
 * - handleCarryToggle:孩子坐着 → 提示"need_standing"直接拒绝;
 * - resolveTargetChild:孩子坐着 → 找不到孩子("no_child");
 * - resolveCarrierAdult:妈妈坐着 → 找不到妈妈("no_mother");
 * 而本 mod 的幼女约束(强制坐下)让幼儿女儿一直坐着——抱起键永远无效。
 *
 * 修复:@Redirect 这三处 isMaidInSittingPose 调用恒返回 false(假装未坐下)——
 * 坐下/站起都能触发"让妈妈抱";骑乘检查(m_20159_)保留(已被抱着时不重复抱)。
 * onPlayerTick 的"妈妈一出现自动抱起"机制【不动】——原版保留。
 *
 * maidmarriage 类不在编译 classpath:@Pseudo + 字符串 targets + optional 配置。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.compat.MaidCarryChildManager")
public abstract class CarryToggleSittingMixin {

    @Redirect(method = "handleCarryToggle|resolveTargetChild|resolveCarrierAdult",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isMaidInSittingPose()Z"))
    private static boolean heartfelt$allowCarryWhileSitting(EntityMaid maid) {
        return false; // 坐下/站起都能触发"让妈妈抱"
    }
}
