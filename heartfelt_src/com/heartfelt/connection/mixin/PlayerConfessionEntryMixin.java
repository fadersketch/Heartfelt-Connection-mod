package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.client.PlayerConfessionScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 告白方向修正(v1.3.0,P0)——客户端 mixin。
 *
 * 玩家在 maidmarriage 的 HugActionScreen 对话菜单点"表白"(choiceId="confession")
 * 时,原版会跳 confession_intro 剧本让【女仆开口告白】——galgame 方向错位。
 * 本 mixin 拦截 HugDialogueRuntimeBridge.choose(String):当 choiceId=="confession"
 * 且 heartfelt 玩家告白开启时,不进入 maidmarriage 剧本,改为:
 *   1. 从 HugClientState.getLocalInteractionMaidUuid() 读当前交互女仆 UUID;
 *   2. 找到该女仆实体,打开 heartfelt 的 PlayerConfessionScreen(玩家开口+女仆回应);
 *   3. 返回 true 吞掉选择(maidmarriage 不再推进剧本)。
 *
 * v1.5.362 加固:UUID 找不到时按女仆名兜底(bridge 的 maidNameSupplier,64 格内同名女仆)。
 * v1.5.363:移除 jumpToNode 拦截——它过宽,会误劫【女仆主动告白】的 confession_* 续播
 * (女仆主动告白是合法流程,不该被换成玩家告白屏)。
 *
 * 目标类仅客户端存在,且不在编译 classpath → 字符串 targets + 反射读取状态。
 * 注意:必须放在 mixins.heartfelt.json 的 client 段(服务端无此类,会崩)。
 */
@Mixin(targets = "com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueRuntimeBridge")
public abstract class PlayerConfessionEntryMixin {

    /** 交互女仆名提供器(桥构造器注入;兜底查找用) */
    @Shadow
    private Supplier<String> maidNameSupplier;

    @Inject(method = "choose", at = @At("HEAD"), cancellable = true)
    private void heartfelt$interceptPlayerConfession(String choiceId, CallbackInfoReturnable<Boolean> cir) {
        if (!"confession".equals(choiceId)) {
            return;
        }
        if (heartfelt$openConfessionScreen()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * v1.5.365:女仆主动告白被拒("……先让我缓缓"= confession_reject 选项)——maidmarriage
     * 的拒绝选项无服务端动作,记忆/情绪零影响;这里拦截选择,发 C2S ConfessionRejectPacket
     * 让服务端写永久记忆(CONFESSION_FAILED)+ 心情惩罚。
     */
    @Inject(method = "choose", at = @At("HEAD"))
    private void heartfelt$reportConfessionReject(String choiceId, CallbackInfoReturnable<Boolean> cir) {
        if (!"confession_reject".equals(choiceId)) {
            return;
        }
        UUID maidUuid = heartfelt$interactionMaidUuid();
        if (maidUuid != null) {
            try {
                com.heartfelt.connection.network.HeartfeltNetwork.channel().sendToServer(
                        new com.heartfelt.connection.network.HeartfeltNetwork.ConfessionRejectPacket(maidUuid));
            } catch (Exception ignored) {
            }
        }
    }

    /** 尝试打开 heartfelt 告白屏;成功返回 true */
    @Unique
    private boolean heartfelt$openConfessionScreen() {
        if (!com.heartfelt.connection.config.HeartfeltConfig.PLAYER_CONFESSION_ENABLED.get()) {
            return false;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null || mc.f_91074_ == null) {
            return false;
        }
        // ① 按交互状态 UUID 找(主路径)
        EntityMaid target = heartfelt$findMaidByUuid(mc, heartfelt$interactionMaidUuid());
        // ② v1.5.362 兜底:UUID 落空(交互状态被 tick 清空)时按女仆名找
        if (target == null && this.maidNameSupplier != null) {
            String name = this.maidNameSupplier.get();
            if (name != null && !name.isEmpty()) {
                target = heartfelt$findMaidByName(mc, name);
            }
        }
        if (target == null) {
            return false;
        }
        // 关闭 maidmarriage 的 HugActionScreen,打开 heartfelt 告白屏
        mc.m_91152_(new PlayerConfessionScreen(target));
        return true;
    }

    @Unique
    private static UUID heartfelt$interactionMaidUuid() {
        try {
            Class<?> cls = Class.forName("com.example.maidmarriage.client.HugClientState");
            Object value = cls.getMethod("getLocalInteractionMaidUuid").invoke(null);
            return value instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static EntityMaid heartfelt$findMaidByUuid(Minecraft mc, UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        for (EntityMaid maid : mc.f_91073_.m_45976_(
                EntityMaid.class, mc.f_91074_.m_20191_().m_82400_(64.0))) {
            if (maid.m_20148_().equals(maidUuid)) {
                return maid;
            }
        }
        return null;
    }

    @Unique
    private static EntityMaid heartfelt$findMaidByName(Minecraft mc, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (EntityMaid maid : mc.f_91073_.m_45976_(
                EntityMaid.class, mc.f_91074_.m_20191_().m_82400_(64.0))) {
            if (name.equals(maid.m_7755_().getString())) {
                return maid;
            }
        }
        return null;
    }
}
