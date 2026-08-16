package com.heartfelt.connection.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.network.HeartfeltNetwork;
import com.heartfelt.connection.prompt.PromptTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
    /** 面板最大宽;小窗口自适应 */
    private static final int PANEL_W_MAX = 340;
    /** 面板底边距屏幕底部(px) */
    private static final int PANEL_MARGIN_BOTTOM = 20;
    /** v1.5.22:面板内部填充——粉色半透明(不再黑漆漆) */
    private static final int PANEL_FILL = 0x9AF7C9DD;
    /** v1.5.22:面板描边——粉色透明 */
    private static final int PANEL_BORDER = 0xC8E89BC8;

    private final EntityMaid maid;
    private final String maidName;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
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
        this.lines.clear();
        String body = switch (this.state) {
            case 0 -> PromptTexts.playerConfessionIntro(this.maidName);
            case 1 -> PromptTexts.PLAYER_CONFESSION_WAITING;
            default -> this.maidResponse;
        };
        this.lines.addAll(this.f_96547_.m_92923_(
                Component.m_237113_(body), this.panelWidth - 40));
        int textHeight = this.lines.size() * 10;
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
        int textHeight = this.lines.size() * 10;
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
        for (FormattedCharSequence line : this.lines) {
            // v1.5.22:每行按自身宽度居中(不再贴左)
            int lineWidth = (int) this.f_96547_.m_92865_().m_92336_(line);
            int x = left + Math.max(0, (this.panelWidth - lineWidth) / 2);
            graphics.m_280364_(this.f_96547_, line, x, y, 0xFFFFFF);
            y += 10;
        }
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
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
