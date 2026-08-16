package com.heartfelt.connection.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 心契对话面板正文居中(v1.5.103)——用户反馈对话框弹出来的内容仍偏左,要求居中。
 *
 * 反编译实证:maidmarriage 的 DialogueBoxComponent.render 里正文用
 * DialogueUiRender.drawWrappedScaledText(GuiGraphics, Font, Component, int x,
 * int y, int maxWidth, float scale, int color) 绘制——内部 m_280554_
 * (drawWordWrap)从 x=20% 起【左对齐换行】,整块贴左。
 *
 * 修复:@Redirect 拦截该调用,改为【按 maxWidth 用 Font.split 拆行,每行按
 * 自身宽度居中后逐行绘制】。参数(组件算好的 textX/textY/wrapWidth/textScale/
 * textColor)原样传入,只把"整块左对齐"换成"逐行居中"。
 * 对全部对话(女儿/普通女仆)统一生效——maidmarriage 原版本就偏左,居中对
 * 所有女仆都是改善(用户之前已把 heartfelt 的告白屏改成居中,这里同风格)。
 *
 * v1.5.113:handler 必须与【被注入方法 render】同为实例方法——Mixin 0.8.5
 * checkTargetModifiers 比较的是 target 方法(render)与 handler 的 static 标志,
 * 不是被 redirect 的调用。旧版 handler 是 static → 告白界面(maidmarriage
 * HugActionScreen)第一次真正打开、加载 DialogueBoxComponent 时
 * MixinApplyError 崩溃(此前告白界面从未弹出来,该 mixin 从未被应用)。
 *
 * maidmarriage 类不在编译 classpath → @Pseudo + 字符串 targets。
 * 客户端专用 → mixins.heartfelt.json 的 client 段。
 */
@Pseudo
@Mixin(targets = "com.example.maidmarriage.client.dialogueui.DialogueBoxComponent")
public abstract class DialogueBoxCenteredMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/maidmarriage/client/dialogueui/DialogueUiRender;drawWrappedScaledText(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIFI)V"),
            require = 0)
    private void heartfelt$drawCenteredText(GuiGraphics graphics, Font font, Component text,
            int x, int y, int maxWidth, float scale, int color) {
        if (graphics == null || font == null || text == null || maxWidth <= 0) {
            return;
        }
        // 按 wrapWidth 拆行(与原 drawWrappedScaledText 的 maxWidth 一致)
        List<FormattedCharSequence> lines = font.m_92923_(text, maxWidth);
        if (lines.isEmpty()) {
            return;
        }
        // 逐行居中:行宽 = StringSplitter.width(line);
        // 居中起点 = x + (maxWidth - 行宽) / 2
        int lineHeight = font.f_92710_ + 2; // fontHeight + 2(与原 drawWordWrap 行距一致)
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float lineWidth = font.m_92865_().m_92336_(line); // StringSplitter.width
            int centeredX = x + Math.max(0, Math.round((maxWidth - lineWidth) / 2.0f));
            int lineY = y + Math.round(i * lineHeight * scale);
            // 按原 scale 绘制(仿 drawScaledText:push → scale → drawString → pop)
            graphics.m_280168_().m_85836_();
            graphics.m_280168_().m_85841_(scale, scale, 1.0f);
            graphics.m_280364_(font, line,
                    Math.round(centeredX / scale), Math.round(lineY / scale), color);
            graphics.m_280168_().m_85849_();
        }
    }
}
