package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 坐姿女仆 Alt+J 辅助瞄准(v1.5.37 幼儿女儿 → v1.5.60 扩展到所有女仆)。
 *
 * 根因:TLM 的 AI 聊天打开链(PressAIChatKeyEvent.maidCheck)只认
 * Minecraft 每帧准星命中(Minecraft.f_91077_ 为 EntityHitResult)——坐着的女仆
 * (幼儿女儿实体小/坐姿姿态)准星射线极易穿过她瞄到背后的方块/地面,hitResult
 * 是 BlockHitResult,maidCheck 返回 null,Alt+J 无反应(不是代码阻止,是"瞄不准")。
 *
 * 修复:包裹 TLM 的 maidCheck(private static)——原判定成功则原样返回;
 * 失败时兜底:玩家视线前方 10 格内最近、且属于该玩家的女仆,返回她。
 * v1.5.60:兜底从"仅 maidmarriage 女儿实体(MaidChildEntity)"扩展到【所有女仆】
 * ——普通女仆/成女女儿坐着(等妈妈/强制坐/手动坐)同样瞄不准,一并兜底;
 * 视线锥角 32°→41°(cos 0.85→0.75),坐下的小女仆低头视角也能命中。
 * 潜行检查保留(TLM 原版潜行时 Alt+J 无效)。
 *
 * TLM 类在编译 classpath(original_tlm.jar)→ 普通 @Mixin;客户端 mixin
 * (mixins.heartfelt.json client 段)。
 */
@Mixin(PressAIChatKeyEvent.class)
public abstract class MaidCheckFallbackMixin {

    @WrapMethod(method = "maidCheck")
    private static EntityMaid heartfelt$maidCheckFallback(Operation<EntityMaid> original) {
        EntityMaid maid = original.call();
        if (maid != null) {
            return maid;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || mc.f_91074_ == null || mc.f_91073_ == null) {
            return null;
        }
        LocalPlayer player = mc.f_91074_;
        if (player.m_5833_()) {
            return null; // 保留 TLM 原版潜行限制
        }
        Vec3 look = player.m_20154_();
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityMaid m : mc.f_91073_.m_45976_(EntityMaid.class,
                player.m_20191_().m_82400_(10.0))) {
            // v1.5.60:不限女儿实体——所有属于该玩家的女仆(坐姿/小体型)都兜底
            if (!m.m_6084_() || m.m_269323_() != player) {
                continue;
            }
            Vec3 to = m.m_20182_().m_82559_(player.m_20182_());
            double distSq = to.m_82553_();
            if (distSq > 100.0 || distSq >= bestDist) {
                continue;
            }
            // 视线锥:玩家看向女仆的方向与视线夹角 ~41° 内(v1.5.60 放宽,
            // 坐下的小女仆在玩家脚下/斜下方时也能兜底命中)
            double cos = to.m_82554_(look) / Math.sqrt(distSq);
            if (cos > 0.75) {
                best = m;
                bestDist = distSq;
            }
        }
        return best;
    }
}
