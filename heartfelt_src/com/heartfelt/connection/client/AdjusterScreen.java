package com.heartfelt.connection.client;

import com.heartfelt.connection.network.HeartfeltNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调整器 GUI(v1.4.6)——格式与 Promaid 手册一致的纯客户端 Screen。
 *
 * 打开:手持调整器右击女仆 → 服务端 OpenAdjusterPacket → 本界面;
 * 按钮:点击 → C2S AdjusterActionPacket → 服务端 applyAction → 回发
 * OpenAdjusterPacket 原地刷新状态(同一女仆时 refresh,不重开界面)。
 *
 * 两页:第 1 页基础(好感/关系/心情/情绪/阶段/记忆/清理),
 * 第 2 页高级(场景模拟/数值精度/实用/关系细节);底部翻页。
 * 与手册一致:暂停世界(isPauseScreen=true,单机打开时世界静止),
 * 设置完成后 ESC 关闭界面观察反应。
 */
public class AdjusterScreen extends Screen {
    /** 面板最大宽;小窗口(高 GUI 缩放)时收窄,永不超屏 */
    private static final int PANEL_W_MAX = 340;
    /** 面板最大高(标题+状态+结果+8 行按钮+翻页+边距;小窗口由 panelH() 钳制) */
    private static final int PANEL_H_MAX = 266;
    /** 面板最小高(小窗口保底;panelH() 会在 guiH 更小时进一步收缩保证不超屏) */
    private static final int PANEL_H_MIN = 238;
    /** v1.5.41:行高 21→18、按钮高 18→16——第 2 页 8 行在矮窗口(guiH≥254)也能完整放下 */
    private static final int ROW_H = 18;
    private static final int BTN_H = 16;

    private final UUID maidUuid;
    private final String maidName;
    private final List<String> statusLines = new ArrayList<>();
    /** v1.4.10:最近一次操作结果(黄色结果行);null 不显示 */
    private String result = null;
    private int page = 1;

    public AdjusterScreen(HeartfeltNetwork.OpenAdjusterPacket packet) {
        super(Component.m_237113_("调整器"));
        this.maidUuid = packet.maidUuid;
        this.maidName = packet.maidName;
        this.statusLines.addAll(packet.statusLines);
        this.result = packet.result;
    }

    /** 是否为同一女仆(用于原地刷新判断) */
    public boolean sameMaid(UUID uuid) {
        return this.maidUuid.equals(uuid);
    }

    /** 服务端回发刷新:更新状态行/结果并重建按钮(不关闭界面) */
    public void refresh(HeartfeltNetwork.OpenAdjusterPacket packet) {
        this.statusLines.clear();
        this.statusLines.addAll(packet.statusLines);
        this.result = packet.result;
        rebuildButtons();
    }

    @Override
    public void m_7856_() {
        rebuildButtons();
    }

    @Override
    public boolean m_7043_() {
        // v1.5.7:选择性静止由服务端 DialogueFreezeManager 做(其他生物定住,
        // 玩家与目标女仆仍可移动)——界面本身不暂停。
        return false;
    }

    @Override
    public void m_7379_() {
        // v1.5.7:关闭界面 → 通知服务端恢复被静止的生物
        HeartfeltNetwork.channel().sendToServer(
                new HeartfeltNetwork.AdjusterActionPacket(this.maidUuid, "close"));
        super.m_7379_();
    }

    /** 面板实际宽(v1.4.8:随窗口收窄,留 12px 边距,小窗口不高 GUI 缩放不超屏) */
    private int panelW() {
        return Math.min(PANEL_W_MAX, Math.max(120, this.f_96543_ - 24));
    }

    /** 面板实际高(v1.4.8:小窗口收窄;低于最小可用值时按钮区与翻页重叠) */
    private int panelH() {
        return Math.min(PANEL_H_MAX, Math.max(PANEL_H_MIN, this.f_96544_ - 24));
    }

    /** 重建全部按钮(翻页/刷新时调用) */
    private void rebuildButtons() {
        this.m_169413_(); // clearWidgets
        int cx = this.f_96543_ / 2;
        // v1.5.16:面板位置钳制——guiW/guiH 小于面板时不再居中溢出屏幕(左/上至少留 4px)
        int top = Math.max(4, this.f_96544_ / 2 - panelH() / 2);
        int y = top + 58; // 标题 + 状态 + 结果行之下
        if (this.page == 1) {
            y = addRow(y,
                    "好感+10", "favor+10", "好感-10", "favor-10", "=64", "favor=64", "=128", "favor=128");
            y = addRow(y,
                    "=192", "favor=192", "=384", "favor=384", "恋人", "rel=lover", "妻子", "rel=wife");
            y = addRow(y,
                    "女儿", "rel=child", "解除关系", "rel=none", "心情+5", "mood+5", "心情-5", "mood-5");
            y = addRow(y,
                    "心情15", "mood=15", "心情25", "mood=25", "信任+20", "trust+20", "恐惧-20", "fear-20");
            y = addRow(y,
                    // v1.5.59:成长阶段 4 档(婴儿/幼儿/少女/成年)——原"幼年"一档拆分为婴儿+幼儿
                    "婴儿", "stage=infant", "幼儿", "stage=juvenile", "少女", "stage=child", "成年", "stage=adult");
            y = addRow(y,
                    "首见=今天", "mem=firstmeet", "告白=今天", "mem=confession",
                    "清伤心", "clear=hurt", "清背叛", "clear=betrayal");
            addRow(y, "奶=3", "milk=3");
        } else {
            y = addRow(y,
                    "设背叛", "betray=on", "设伤心", "hurt=1d", "等妈妈", "wait=mother");
            y = addRow(y,
                    "产后1天", "post=1d", "丧子哀悼", "grief=child", "信任=100", "trust=100", "信任=0", "trust=0");
            y = addRow(y,
                    "恐惧=0", "fear=0", "恐惧=100", "fear=100", "饥饿10", "hunger=10", "饥饿50", "hunger=50");
            y = addRow(y,
                    "饥饿90", "hunger=90", "心情5", "mood=5", "心情10", "mood=10", "心情20", "mood=20");
            y = addRow(y,
                    "调试信息", "debug", "拉回", "pull", "坐下", "pose=1", "站起", "pose=0");
            y = addRow(y,
                    "时间+1天", "time+1d", "认主", "tame=on", "解除认主", "tame=off", "告白失败", "fail=1");
            y = addRow(y, "心碎", "broken=1", "思慕1天", "long=1d", "破裂×1", "break=1",
                    "纪念日重置", "anniv=reset");
            // v1.5.16:第 8 行(纪念日测试)自适应——窗口高度不足时隐藏,避免压住底部翻页按钮
            // v1.5.41:取消隐藏——用户反馈"时间+100天"等按钮没出现(矮窗口整行被藏);
            // 行高压缩后 8 行在 guiH≥254 完整放下,不再依赖隐藏
            addRow(y, "首见7天前", "anniv=7", "时间+7天", "time+7d",
                    "时间+30天", "time+30d", "时间+100天", "time+100d");
        }
        // 底部翻页(固定居中)
        int by = top + panelH() - 28;
        this.m_142416_(Button.m_253074_(Component.m_237113_("\u00a77\u25c0 第1页"), b -> switchPage(1))
                .m_252987_(cx - 74, by, 68, 18).m_253136_());
        this.m_142416_(Button.m_253074_(Component.m_237113_("第2页 \u25b6"), b -> switchPage(2))
                .m_252987_(cx + 6, by, 68, 18).m_253136_());
    }

    /** 翻页:切页并重建按钮 */
    private void switchPage(int page) {
        this.page = page;
        rebuildButtons();
    }

    /** 一排按钮(label, action 成对);列宽/按钮宽随面板自适应;返回下一行 y */
    private int addRow(int y, String... pairs) {
        int panelW = panelW();
        int left = (this.f_96543_ - panelW) / 2 + 8;
        int colW = (panelW - 16) / 4;
        int btnW = Math.max(48, colW - 4);
        for (int i = 0; i < pairs.length; i += 2) {
            String label = pairs[i];
            String action = pairs[i + 1];
            int x = left + (i / 2) * colW;
            this.m_142416_(Button.m_253074_(Component.m_237113_(label),
                    b -> HeartfeltNetwork.channel().sendToServer(
                            new HeartfeltNetwork.AdjusterActionPacket(maidUuid, action)))
                    .m_252987_(x, y, btnW, BTN_H).m_253136_());
        }
        return y + ROW_H;
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.m_280039_(graphics); // renderBackground(变暗背景)
        int panelW = panelW();
        int panelH = panelH();
        // v1.5.16:面板位置钳制——guiW/guiH 小于面板时从屏幕边角开始,不再居中溢出屏幕
        int left = Math.max(4, (this.f_96543_ - panelW) / 2);
        int top = Math.max(4, (this.f_96544_ - panelH) / 2);
        // 外框 + 面板
        graphics.m_280509_(left - 4, top - 4, left + panelW + 4, top + panelH + 4, 0xC0101010);
        graphics.m_280509_(left, top, left + panelW, top + panelH, 0xB01B1B1B);
        // 标题(居中于面板中心)
        graphics.m_280430_(this.f_96547_,
                Component.m_237113_("\u00a7d调整器 \u00a7f· " + this.maidName
                        + (this.page == 2 ? " \u00a78(第2页)" : "")),
                left + panelW / 2, top + 9, 0xFFFFFF);
        // 状态行(v1.5.16:按面板宽度截断,超长不再溢出面板右缘;
        //  v1.5.61:居中显示——用户反馈最上面的字段偏左,应移至正中间)
        int sy = top + 26;
        int statusMax = Math.max(80, panelW - 24);
        for (String line : this.statusLines) {
            graphics.m_280430_(this.f_96547_,
                    Component.m_237113_(clipToWidth(line, statusMax)),
                    left + panelW / 2, sy, 0xFFFFFF);
            sy += 11;
        }
        // v1.4.10:操作结果反馈(黄色;v1.5.61 同状态行居中)
        if (this.result != null) {
            graphics.m_280430_(this.f_96547_,
                    Component.m_237113_("\u00a7e" + clipToWidth(this.result, statusMax)),
                    left + panelW / 2, top + 48, 0xFFFFFF);
        }
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
    }

    /** v1.5.16:按像素宽度截断文本(末尾加省略号),防状态行/结果溢出面板 */
    private String clipToWidth(String s, int maxPx) {
        if (s == null || this.f_96547_.m_92895_(s) <= maxPx) {
            return s;
        }
        String t = s;
        while (!t.isEmpty() && this.f_96547_.m_92895_(t + "\u2026") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "\u2026";
    }
}
