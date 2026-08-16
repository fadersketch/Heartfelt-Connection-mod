package com.heartfelt.connection.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * v1.4.4 修复：Screen 的 protected 成员访问器。
 *
 * AIChatScreenDramatizeMixin 原用 @Shadow 拿 Screen.width/height(f_96543_/f_96544_)
 * 与 addRenderableWidget(m_142416_)——AIChatScreen 继承自 Screen，这些成员都在
 * 父类上；Mixin 的 @Shadow 方法在目标类解析失败（"was not located in the target
 * class ... AIChatScreen"）→ 启动崩溃。改用标准 @Accessor/@Invoker 访问器，
 * 打在声明处（Screen 自身）保证解析。
 */
@Mixin(net.minecraft.client.gui.screens.Screen.class)
public interface ScreenWidgetAccessor {

    /** Screen.width（protected int） */
    @Accessor("f_96543_")
    int heartfelt$screenWidth();

    /** Screen.height（protected int） */
    @Accessor("f_96544_")
    int heartfelt$screenHeight();

    /** Screen.addRenderableWidget（protected，泛型 T extends GuiEventListener & Renderable & NarratableEntry） */
    @Invoker("m_142416_")
    <T extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> T heartfelt$addRenderableWidget(T widget);
}
