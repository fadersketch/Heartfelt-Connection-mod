package com.heartfelt.connection.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 心契对话面板正文居中(v1.5.103)——用户反馈对话框弹出来的内容仍偏左,要求居中。
 *
 * 反编译实证:maidmarriage 的 DialogueBoxComponent.render 里正文用
 * DialogueUiRender.drawWrappedScaledText(GuiGraphics, Font, Component, int x,
 * int y, int maxWidth, float scale, int color) 绘制——内部 m_280554_
 * (drawWordWrap)从 x 起【左对齐换行】,整块贴左。
 *
 * v1.5.386 修复:旧版用 Font.split(m_92923_)拆行后逐行居中——但 MC 1.20.1 的
 * StringSplitter 只在【空格】处断行,中文无空格整段被当作一个超宽行原样返回,
 * (maxWidth - 行宽)/2 得到大负数 → 文字画到面板左边,压住立绘、挤出屏幕
 * (用户:"显示应该再往右移动,否则还会挤出屏幕,文本与人物头像重叠")。
 * 与 PlayerConfessionScreen 同方案:按【像素宽度逐字符】切行,保证每行
 * 物理宽度 ≤ maxWidth,居中偏移恒非负,文字必然落在面板内且不碰立绘。
 *
 * v1.5.113:handler 必须与【被注入方法 render】同为实例方法——Mixin 0.8.5
 * checkTargetModifiers 比较的是 target 方法(render)与 handler 的 static 标志,
 * 不是被 redirect 的调用。
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
        // v1.5.386:原方法在 scale 变换内以 maxWidth/scale 为换行宽(未缩放单位)——
        // 像素切行必须用同一基准,否则 scale>1 时行宽仍会溢出
        int wrapUnscaled = Math.max(1, Math.round(maxWidth / scale));
        List<String> rows = heartfelt$splitByPixelWidth(font, text.getString(), wrapUnscaled);
        if (rows.isEmpty()) {
            return;
        }
        int lineHeight = font.f_92710_ + 2; // fontHeight + 2(与原 drawWordWrap 行距一致)
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            if (row.isEmpty()) {
                continue; // 段落间空行:占位不绘制
            }
            // v1.5.386b:不再经过 Font.split(m_92923_)——PlayerConfessionScreen 的
            // 像素取证证明它对无空格中文会【吞行/截头】(整段只渲染部分行,行首缺字)。
            // 行宽直接用 Font.width(String) 计算,绘制用 drawString(Font, Component)
            // 单行 API(m_280430_),绝不换行、绝不丢字。
            int lineWidth = font.m_92895_(row);
            // 行起点:在 [x, x+maxWidth] 文本区内居中——短行自动离开左缘(不再
            // 贴左边压住立绘/头像),长行(接近满宽)自然从左缘附近开始
            int centeredX = x + Math.max(0, Math.round((maxWidth - lineWidth * scale) / 2.0f));
            int lineY = y + Math.round(i * lineHeight * scale);
            // 按原 scale 绘制(仿 drawScaledText:push → scale → drawString → pop)
            graphics.m_280168_().m_85836_();
            graphics.m_280168_().m_85841_(scale, scale, 1.0f);
            graphics.m_280430_(font, Component.m_237113_(row),
                    Math.round(centeredX / scale), Math.round(lineY / scale), color);
            graphics.m_280168_().m_85849_();
        }
    }

    /** v1.5.386:按像素宽度逐字符切行(中文无空格,Font.split 不会自动断行)。
     *  先按 \n 拆段保留段落结构;段首 §X 颜色码提取为前缀,切出的每行补回——
     *  整段颜色在多行下不丢。空段返回空串占位(渲染时跳过但保留行距)。 */
    @Unique
    private static List<String> heartfelt$splitByPixelWidth(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder prefix = new StringBuilder();
            int i = 0;
            while (i + 1 < paragraph.length() && paragraph.charAt(i) == '\u00a7') {
                prefix.append(paragraph.charAt(i)).append(paragraph.charAt(i + 1));
                i += 2;
            }
            String body = paragraph.substring(i);
            StringBuilder line = new StringBuilder();
            int lineW = 0;
            for (int c = 0; c < body.length(); c++) {
                String ch = String.valueOf(body.charAt(c));
                int cw = font.m_92895_(ch);
                if (lineW + cw > maxWidth && line.length() > 0) {
                    out.add(prefix + line.toString());
                    line.setLength(0);
                    lineW = 0;
                }
                line.append(ch);
                lineW += cw;
            }
            out.add(prefix + line.toString());
        }
        return out;
    }
}
