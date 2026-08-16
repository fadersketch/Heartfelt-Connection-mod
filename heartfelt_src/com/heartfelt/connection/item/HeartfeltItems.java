package com.heartfelt.connection.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * heartfelt_connection 物品注册(v1.4.3)。
 *
 * 物品命名空间 = modId "heartfelt_connection";创造栏注入原版「工具与杂项」
 * 页(搜索栏自动包含已注册物品,无需额外处理)。无合成配方(data 不建 recipes)
 * → 仅创造栏 + 指令获取,符合"测试工具不进入生存流程"的设计。
 */
public final class HeartfeltItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "heartfelt_connection");

    /** 调整器(测试工具):形象=纸,右击女仆打开调整菜单 */
    public static final RegistryObject<Item> ADJUSTER =
            ITEMS.register("adjuster", () -> new AdjusterItem(new Item.Properties().m_41487_(1)));

    private HeartfeltItems() {
    }

    /** 创造栏注入:工具与杂项页 */
    @Mod.EventBusSubscriber(modid = "heartfelt_connection", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CreativeTabs {
        private static final ResourceKey<CreativeModeTab> TAB_TOOLS_AND_UTILITIES =
                ResourceKey.m_135785_(Registries.f_279569_,
                        new ResourceLocation("minecraft", "tools_and_utilities"));

        @SubscribeEvent
        public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
            if (!event.getTabKey().equals(TAB_TOOLS_AND_UTILITIES)) {
                return;
            }
            event.accept(ADJUSTER);
        }
    }
}
