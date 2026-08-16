package com.heartfelt.connection.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 调整器(v1.4.3)——测试工具物品。
 *
 * 形象 = 原版纸(assets 模型复用 minecraft:item/paper 贴图);右击女仆打开
 * 聊天栏按钮菜单(AdjusterManager.openMenu),一键调整好感/关系/心情/信任恐惧/
 * 成长阶段/记忆/清理状态。
 *
 * 仅创造栏(tools_and_utilities)+ 指令获取;无合成、无自然生成。
 * 所有交互逻辑在 AdjusterManager(右击事件)与 HeartfeltCommand(按钮命令)侧,
 * 本类只负责物品属性与 tooltip。
 */
public class AdjusterItem extends Item {
    public AdjusterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void m_7373_(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.m_237113_("\u00a77右击女仆打开调整菜单"));
    }

    /** v1.4.5:附魔光泽特效(让测试工具看起来特殊一点) */
    @Override
    public boolean m_5812_(ItemStack stack) {
        return true;
    }
}
