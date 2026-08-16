package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 爱憎分明 × 心契誓约隔离——背叛(v1.5.26;v1.1.0 A0 包名修正 + A2 标记过期)。
 *
 * 1. 关系女仆(恋人/妻子/女儿)**永不背叛**:拦截 triggerBetrayal(HEAD cancel)——
 *    信任/恐惧异常也不会攻击主人、破坏箱子、爆炸。
 * 2. 背叛女仆的**攻击目标优先级**:主人 > 关系女仆(妻子/女儿)> 正常女仆——
 *    在 selectTarget 返回后改写:若选中的不是主人,优先替换为附近的关系女仆。
 *    v1.1.0 修复死代码:callresponse 的 triggerBetrayal 会清空 owner UUID,旧版
 *    用 getOwner 找主人恒为 null;现把"被恨的玩家"UUID + 时间戳写入 ForgeData。
 *
 * v1.1.0(A2):hated_player 带 hated_at 时间戳,超期(配置天数)自动清除——
 * 修永久残留:女仆一旦背叛过,仇恨标记永不失效。
 *
 * @Pseudo + 字符串类名:Love Loathe 可选,未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager")
public abstract class EmotionBetrayalIsolationMixin {
    /** 关系女仆永不背叛;非关系女仆背叛瞬间记录被恨的玩家+时间戳(供目标改写用) */
    @Inject(method = "triggerBetrayal", at = @At("HEAD"), cancellable = true)
    private static void maidsmart$noBetrayalForRelated(EntityMaid maid, ServerPlayer player, CallbackInfo ci) {
        if (RelationshipExemption.isFrozen(maid)) {
            ci.cancel();
            return;
        }
        // 背叛后 owner 会被清空,先记住"她恨的是谁"与发生时间
        maid.getPersistentData().m_128362_(HeartfeltTags.HATED_PLAYER, player.m_20148_());
        maid.getPersistentData().m_128356_(HeartfeltTags.HATED_AT, maid.m_9236_().m_46467_());
    }

    /** 背叛女仆选目标:非主人时优先替换为附近关系女仆(仇恨:主人 > 妻子/女儿 > 普通女仆) */
    @Inject(method = "selectTarget", at = @At("RETURN"), cancellable = true)
    private static void maidsmart$relatedMaidPriority(EntityMaid maid, CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity chosen = cir.getReturnValue();
        if (chosen instanceof ServerPlayer) {
            return; // 主人最高优先,保持
        }
        ServerPlayer player = hatedPlayer(maid);
        if (player == null) {
            return;
        }
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityMaid m : maid.m_9236_().m_45976_(EntityMaid.class, maid.m_20191_().m_82400_(32.0))) {
            if (m == maid || !m.m_6084_()) {
                continue;
            }
            // A3:UUID 比较代替引用比较(跨维度/重进不再失效)
            if (!DialogueDispatcher.isOwner(m, player)) {
                continue;
            }
            if (!RelationshipExemption.isFrozen(m)) {
                continue; // 只优先关系女仆
            }
            double d = maid.m_20270_(m);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        if (best != null) {
            cir.setReturnValue(best);
        }
    }

    /** 从 ForgeData 读"被恨的玩家"并解析为在线 ServerPlayer;无/超期返回 null */
    private static ServerPlayer hatedPlayer(EntityMaid maid) {
        try {
            java.util.UUID uuid = maid.getPersistentData().m_128342_(HeartfeltTags.HATED_PLAYER);
            if (uuid == null) {
                return null;
            }
            // A2:超期自动清除(配置保留天数)
            long retentionTicks = HeartfeltConfig.HATED_PLAYER_RETENTION_DAYS.get() * 24000L;
            long hatedAt = maid.getPersistentData().m_128454_(HeartfeltTags.HATED_AT);
            if (hatedAt > 0L && maid.m_9236_().m_46467_() - hatedAt > retentionTicks) {
                maid.getPersistentData().m_128473_(HeartfeltTags.HATED_PLAYER);
                maid.getPersistentData().m_128473_(HeartfeltTags.HATED_AT);
                return null;
            }
            if (!(maid.m_9236_() instanceof ServerLevel level)) {
                return null;
            }
            return level.m_7654_().m_6846_().m_11259_(uuid);
        } catch (Exception e) {
            return null;
        }
    }
}
