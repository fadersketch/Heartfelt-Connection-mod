package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爱憎分明 × 移动流畅度(v1.1.1;v1.1.0 A0 包名修正 + A9 缓存作废条件)。
 *
 * 痛点:HungerManager.applySpeedEffect 每 20 tick(1 秒)直接
 * `AttributeInstance.setBaseValue` 把移动速度基础值强制改成四档(随饥饿阈值跳变)。
 * 女仆边吃边饿,饥饿值持续跨档 → 基础速度每秒猛跳 = 移动一卡一卡("鬼畜")。
 *
 * 做法:拦截 applySpeedEffect(HEAD)——按女仆缓存"当前档位":
 * - 档位没变 + 速度基础值仍是档位值(效果在身)→ 取消本次调用(跳过每秒重复
 *   clobber + 属性刷新 + 网络同步)
 * - 档位变了 / 超过 1 分钟 / 基础值被外部改走(如 promaid 挖矿倍率)→ 放行
 *   原始逻辑重新施加
 *
 * v1.1.0(A9):
 * - 解析失败不永久降级(只缓存成功,下次调用重试)
 * - 增加"效果存在性"作废条件:基础值匹配任一档位速度才跳过——
 *   外部把基础值改走后不再盲目跳过 1 分钟(旧版同档位会跳过 60 秒不纠正)
 *
 * @Pseudo + 字符串类名:Love Loathe 可选,未安装时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.github.JumDa5he.callresponse.compat.hunger.HungerManager")
public abstract class HungerSpeedTierCacheMixin {
    /** 同档位超过此窗口(tick)重新施加一次,兜底外部改基础值 */
    private static final long REAPPLY_WINDOW = 1200L;

    /** callresponse 四档基础速度(与 applySpeedEffect 三元链一致;档 3 为 0.65 缺省档) */
    private static final float[] TIER_SPEEDS = {0.49999997f, 0.54999995f, 0.84999996f, 0.65f};

    /** maid UUID -> {当前档位, 上次施加 gameTime} */
    private static final Map<UUID, long[]> LAST_TIER = new ConcurrentHashMap<>();

    @Inject(method = "applySpeedEffect", at = @At("HEAD"), cancellable = true)
    private static void heartfelt$skipRedundantSpeedClobber(EntityMaid maid, CallbackInfo ci) {
        try {
            float hunger = CallResponseCompat.hungerOf(maid);
            int tier = hunger <= 9.0f || hunger >= 91.0f ? 0
                    : hunger <= 25.0f || hunger >= 75.0f ? 1
                    : (hunger >= 41.0f && hunger <= 74.0f) ? 2 : 3;
            long gameTime = maid.m_9236_().m_46467_();
            long[] prev = LAST_TIER.get(maid.m_20148_());
            if (prev != null && prev[0] == tier && gameTime - prev[1] < REAPPLY_WINDOW) {
                // A9:只有"效果在身"(基础值仍是档位值)才跳过——外部改走后放行重施加
                if (isTierSpeedActive(maid)) {
                    ci.cancel();
                    return;
                }
            }
            LAST_TIER.put(maid.m_20148_(), new long[]{tier, gameTime});
            // 防泄漏:超过 10 分钟的旧条目惰性清理(女仆死亡/离开后不再重复施加)
            if (LAST_TIER.size() > 64) {
                LAST_TIER.entrySet().removeIf(e -> gameTime - e.getValue()[1] > 12000L);
            }
        } catch (Exception ignored) {
            // 反射失败/异常 → 放行原始逻辑(不干预)
        }
    }

    /** 移动速度基础值是否仍是档位值(任一档匹配即视为效果在身) */
    private static boolean isTierSpeedActive(EntityMaid maid) {
        AttributeInstance speed = maid.m_21051_(Attributes.f_22279_);
        if (speed == null) {
            return false;
        }
        double base = speed.m_22135_();
        for (float tierSpeed : TIER_SPEEDS) {
            if (Math.abs(base - tierSpeed) < 0.001) {
                return true;
            }
        }
        return false;
    }
}
