package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.network.HeartfeltNetwork;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LLM 剧情演绎入口 + 对话选择性静止(v1.4.0 演绎按钮;v1.5.5 对话静止)。
 *
 * maidmarriage/心契誓约的固定剧本(告白/丧子/纪念日等)体验差;若玩家配置了
 * LLM,应让 LLM 根据共同记忆【沉浸式演绎剧情】,脱离既定文本。
 *
 * 在 TLM 的 AIChatScreen(alt+J 打开)init 末尾加一个按钮:
 * 点击 → C2S DramatizePacket → 服务端 DialogueDispatcher.dramatize
 * (事件历史 + 演绎提示词 → 女仆 LLM 演绎)→ 回复出现在对话面板。
 *
 * v1.5.5(用户需求):alt+J 对话面板打开期间"其他生物静止,玩家与对话女仆
 * 可移动"——打开面板发 C2S ChatFreezePacket(true),关闭发 false,
 * 服务端 DialogueFreezeManager 选择性静止。其他场合(调整器等)不触发。
 *
 * v1.4.4 修复：原 @Shadow Screen.width/height/addRenderableWidget(m_142416_)——
 * 这些成员声明在父类 Screen 上，Mixin @Shadow 方法在目标类解析失败启动崩溃；
 * 改用 ScreenWidgetAccessor（@Accessor/@Invoker 打在声明处）。
 *
 * TLM 类在编译 classpath(original_tlm.jar)→ 直接 @Mixin(AIChatScreen.class);
 * 客户端专用 → 放 mixins.heartfelt.json 的 client 段。
 */
@Mixin(AIChatScreen.class)
public abstract class AIChatScreenDramatizeMixin {

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void heartfelt$addDramatizeButton(CallbackInfo ci) {
        AIChatScreen screen = (AIChatScreen) (Object) this;
        EntityMaid maid = screen.getMaid();
        if (maid == null) {
            return;
        }
        ScreenWidgetAccessor acc = (ScreenWidgetAccessor) this;
        // 面板居中:输入框宽 330、高 20、位于 (width/2-165, height/2+58)。
        // 按钮放输入框正下方(居中,不遮挡左右按钮列)。
        int width = acc.heartfelt$screenWidth();
        int height = acc.heartfelt$screenHeight();
        int inputY = height / 2 + 58;
        // 只在 AIChatScreen 有空间时添加(小窗口跳过,避免遮挡)
        if (inputY + 56 > height) {
            return;
        }
        acc.heartfelt$addRenderableWidget(Button.m_253074_(
                Component.m_237113_("\u00a7d\u2728 演绎剧情"), b -> {
                    HeartfeltNetwork.channel().sendToServer(
                            new HeartfeltNetwork.DramatizePacket(maid.m_20148_()));
                })
                .m_252987_(width / 2 - 60, inputY + 28, 120, 20)
                .m_253136_());
        // v1.5.5:打开面板 → 服务端选择性静止(其他生物定住,玩家与对话女仆可动)
        HeartfeltNetwork.channel().sendToServer(
                new HeartfeltNetwork.ChatFreezePacket(maid.m_20148_(), true));
    }

    /**
     * v1.5.5:关闭面板 → 服务端恢复被静止的生物。
     * v1.5.14 修复:onClose(m_7379_) 是 Screen 的【继承方法】,AIChatScreen 未声明——
     * 特定加载时机下 Mixin 对继承方法的目标解析会失败(实测启动即
     * InvalidInjectionException 崩溃)。加 require=0 改为非致命:解析不到只警告、
     * 冻结由 DialogueFreezeManager 的 5 分钟无活动超时/登出/保存兜底自动恢复。
     */
    @Inject(method = "m_7379_", at = @At("HEAD"), require = 0)
    private void heartfelt$unfreezeOnClose(CallbackInfo ci) {
        AIChatScreen screen = (AIChatScreen) (Object) this;
        EntityMaid maid = screen.getMaid();
        if (maid == null) {
            return;
        }
        HeartfeltNetwork.channel().sendToServer(
                new HeartfeltNetwork.ChatFreezePacket(maid.m_20148_(), false));
    }
}
