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
 * 爱憎分明 × 心契誓约隔离——争风吃醋（v1.5.26）。
 *
 * 痛点：溺爱（Doting）状态的女仆会攻击主人身边的"情敌"（其他女仆）——
 * 主人已有恋爱对象后这毫无道理。
 *
 * 拦截 EmotionDotingManager 的吃醋驱赶方法（HEAD cancel，返回 boolean →
 * 必须用 CallbackInfoReturnable），满足任一即隔离：
 * - 攻击者本身是关系女仆（恋人/妻子/女儿——她们不争风吃醋）
 * - 主人已拥有任一关系女仆（整体隔离：所有女仆都不再争风吃醋）
 * 其他女仆之间同样不再争风吃醋。
 *
 * @Pseudo + 字符串类名：Love Loathe 可选，未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager")
public abstract class EmotionDotingIsolationMixin {
    // v1.0.2:爱憎分明 2.0.6 将 attackNearbyMaids 改名为 driveNearbyMaids(签名不变)——
    // 双名字数组同时兼容 2.0.4/2.0.6,不存在的名字在 defaultRequire=0 下静默跳过
    @Inject(method = {"attackNearbyMaids", "driveNearbyMaids"}, at = @At("HEAD"), cancellable = true)
    private static void maidsmart$noJealousy(EntityMaid maid, ServerPlayer player, long gameTime, CallbackInfoReturnable<Boolean> cir) {
        if (RelationshipExemption.isFrozen(maid) || RelationshipExemption.ownerHasDedicatedMaid(maid)) {
            cir.cancel();
        }
    }
}
