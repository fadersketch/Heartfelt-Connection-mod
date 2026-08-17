package com.heartfelt.connection.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.network.HeartfeltNetwork;
import com.heartfelt.connection.prompt.PromptTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家主动告白对话界面(v1.3.0,P0 告白方向修正;v1.5.0 改叠加式)。
 *
 * 方向:玩家【开口】对女仆告白,女仆【回应】(接受/委婉)。
 * 形式(v1.5.0,用户要求与心契誓约一致):
 * - 不渲染全屏背景 → 周边环境与女仆实体可见;
 * - 面板改为底部对话框风格(半透明、宽度自适应,小窗口不溢出);
 * - 不暂停游戏。
 *
 * 流程:
 * 1. 玩家在 maidmarriage 的对话菜单点"表白"→ 客户端 mixin 拦截
 *    (PlayerConfessionEntryMixin)关闭 maidmarriage 的 HugActionScreen,
 *    打开本屏——显示玩家要说的告白词 + [郑重说出] [再想想]。
 * 2. [郑重说出] → C2S PlayerConfessionPacket → 进入"等待回应"态。
 * 3. 服务端判定(校验 + 事件历史 + completeConfession)→ S2C
 *    PlayerConfessionResultPacket → receiveResult() 切换到女仆回应文本。
 */
public class PlayerConfessionScreen extends Screen {
    /** 面板最大宽;小窗口自适应（v1.5.386:从 340 加宽到 460——中文无空格，
     *  Font.split 不会在逗号处换行，340 宽度下整段超宽被当一行，居中后 x 为负，
     *  文字溢出面板左边、开头被截掉，用户只看到"面前的新春酒狐…"误读为女仆告白） */
    private static final int PANEL_W_MAX = 460;
    /** 面板底边距屏幕底部(px) */
    private static final int PANEL_MARGIN_BOTTOM = 20;
    /** v1.5.22:面板内部填充——粉色半透明(不再黑漆漆) */
    private static final int PANEL_FILL = 0x9AF7C9DD;
    /** v1.5.22:面板描边——粉色透明 */
    private static final int PANEL_BORDER = 0xC8E89BC8;

    private final EntityMaid maid;
    private final String maidName;
    /** v1.5.386:正文行(纯字符串,渲染时直接 drawString,不走 Font.split) */
    private final List<String> linesRaw = new ArrayList<>();
    private int panelTop;
    private int panelWidth;
    /** 0=玩家开口,1=等待回应,2=女仆回应 */
    private int state = 0;
    private String maidResponse = "";

    public PlayerConfessionScreen(EntityMaid maid) {
        super(Component.m_237113_("Heartfelt-connection"));
        this.maid = maid;
        this.maidName = maid.m_7755_().getString();
    }

    @Override
    protected void m_7856_() {
        // v1.5.23:先清除旧按钮——commit()/receiveResult() 会重调本方法,
        // 不清除会导致各状态按钮叠加(曾出现"郑重说出/好/再想想"三按钮并排)
        this.m_169413_();
        this.panelWidth = Math.min(PANEL_W_MAX, Math.max(200, this.f_96543_ - 24));
        int left = (this.f_96543_ - this.panelWidth) / 2;
        this.linesRaw.clear();
        String body = switch (this.state) {
            case 0 -> PromptTexts.playerConfessionIntro(this.maidName);
            case 1 -> PromptTexts.PLAYER_CONFESSION_WAITING;
            default -> this.maidResponse;
        };
        // v1.5.386:两段式拆行——先按 \n 拆段,再对每段按【像素宽度逐字符切行】。
        // 不再调用 Font.split/StringSplitter(它在无空格中文上行为不可靠:实测
        // 部分行被吞、整段只渲染出 2 行且每行开头缺字——用户截图像素级测量实证)。
        // lines 直接存切好的字符串,渲染时逐行 drawString(原版单行 API,绝不换行/丢字)。
        for (String paragraph : body.split("\n", -1)) {
            this.linesRaw.addAll(
                    splitByPixelWidth(this.f_96547_, paragraph, this.panelWidth - 40));
        }
        int textHeight = this.linesRaw.size() * 10;
        int panelHeight = textHeight + 76;
        // v1.5.0:底部对话框风格(贴近屏幕底部,留出上方空间看世界与女仆)
        this.panelTop = Math.max(8, this.f_96544_ - panelHeight - PANEL_MARGIN_BOTTOM);
        int buttonY = this.panelTop + textHeight + 28;

        if (this.state == 0) {
            // 玩家开口阶段:说出心意 / 再想想
            this.m_142416_(Button.m_253074_(Component.m_237113_("郑重说出"), b -> this.commit())
                    .m_252987_(left + 16, buttonY, 130, 20).m_253136_());
            this.m_142416_(Button.m_253074_(Component.m_237113_("再想想"), b -> this.m_7379_())
                    .m_252987_(left + this.panelWidth - 146, buttonY, 130, 20).m_253136_());
        } else if (this.state == 2) {
            // 女仆回应阶段:好(关闭)
            this.m_142416_(Button.m_253074_(Component.m_237113_("好"), b -> this.m_7379_())
                    .m_252987_(left + this.panelWidth / 2 - 60, buttonY, 120, 20).m_253136_());
        }
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // v1.5.0:不渲染全屏背景(世界与女仆实体可见,与 maidmarriage 一致)
        int left = (this.f_96543_ - this.panelWidth) / 2;
        int textHeight = this.linesRaw.size() * 10;
        int panelHeight = textHeight + 76;
        // v1.5.22:粉色透明对话框(描边粉,内里粉透明)
        graphics.m_280509_(left - 4, this.panelTop - 4, left + this.panelWidth + 4,
                this.panelTop + panelHeight + 4, PANEL_BORDER);
        graphics.m_280509_(left, this.panelTop, left + this.panelWidth,
                this.panelTop + panelHeight, PANEL_FILL);
        String title = switch (this.state) {
            case 0 -> "\u00a7d你的告白\u00a7r → " + this.maidName;
            case 1 -> "\u00a77等待" + this.maidName + "的回应…";
            default -> "\u00a7d" + this.maidName + "\u00a7r 的回应";
        };
        graphics.m_280430_(this.f_96547_,
                Component.m_237113_(title),
                left + this.panelWidth / 2, this.panelTop + 10, 0xFFFFFF);
        int y = this.panelTop + 28;
        for (String row : this.linesRaw) {
            if (!row.isEmpty()) {
                // v1.5.386:直接 drawString 单行绘制(原版 API,不换行不丢字);
                // 每行按自身像素宽居中,切行已保证 ≤ 文本区宽,偏移恒非负
                int lineWidth = this.f_96547_.m_92895_(row);
                int x = left + Math.max(0, (this.panelWidth - lineWidth) / 2);
                // m_280430_ = drawString(Font, Component, x, y, color) 原版单行绘制,
                // 不换行不丢字(比 m_280364_ 的 FormattedCharSequence 重载更直接)
                graphics.m_280430_(this.f_96547_,
                        Component.m_237113_(row), x, y, 0xFFFFFF);
            }
            y += 10;
        }
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
    }

    /** v1.5.386:按像素宽度逐字符切行(中文无空格,Font.split 不会自动断行)。
     *  段首的 §X 颜色码提取为前缀,切出的每行补回——回应态的整段颜色不丢。 */
    private static List<String> splitByPixelWidth(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            out.add("");
            return out;
        }
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '\u00a7') {
            prefix.append(text.charAt(i)).append(text.charAt(i + 1));
            i += 2;
        }
        String body = text.substring(i);
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
        if (line.length() > 0 || out.isEmpty()) {
            out.add(prefix + line.toString());
        }
        return out;
    }

    /** v1.5.0:与 maidmarriage 一致——不暂停世界 */
    @Override
    public boolean m_7043_() {
        return false;
    }

    /** 玩家开口:发出告白请求,进入等待态 */
    private void commit() {
        HeartfeltNetwork.channel().sendToServer(
                new HeartfeltNetwork.PlayerConfessionPacket(this.maid.m_20148_()));
        this.state = 1;
        this.m_7856_();
    }

    /** 服务端回应到达:切换为女仆回应文本 */
    public static void receiveResult(HeartfeltNetwork.PlayerConfessionResultPacket packet) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null || mc.f_91074_ == null) {
            return;
        }
        if (!(mc.f_91080_ instanceof PlayerConfessionScreen screen)) {
            return;
        }
        if (!screen.maid.m_20148_().equals(packet.maidUuid)) {
            return;
        }
        screen.maidResponse = (packet.accepted ? "\u00a7d" : "\u00a77") + packet.responseText;
        screen.state = 2;
        screen.m_7856_();
    }
}
