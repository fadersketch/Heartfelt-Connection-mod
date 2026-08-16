package com.heartfelt.connection.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调整器右击入口(v1.4.5——补漏:1.4.3/1.4.4 只实现了菜单与命令,
 * 忘了注册右击事件,导致拿着调整器右击女仆没有任何效果)。
 *
 * 手持调整器右击女仆 → 取消该次交互(不让 TLM 的原版女仆交互吃掉点击)
 * → 服务端打开聊天栏按钮菜单(AdjusterManager.openMenu)。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class AdjusterInteractHandler {
    private AdjusterInteractHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // 只服务端处理(客户端实体不是 ServerPlayer)
        }
        if (player.m_21205_().m_41720_() != HeartfeltItems.ADJUSTER.get()) {
            return; // 必须手持调整器
        }
        event.setCanceled(true); // 阻止 TLM/原版女仆交互,菜单优先
        AdjusterManager.openGui(player, maid); // v1.4.6:GUI 界面(原聊天栏菜单)
    }
}
