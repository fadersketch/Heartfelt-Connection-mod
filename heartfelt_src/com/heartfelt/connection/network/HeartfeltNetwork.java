package com.heartfelt.connection.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.client.AdjusterScreen;
import com.heartfelt.connection.dialogue.MaidConfessionManager;
import com.heartfelt.connection.item.AdjusterManager;
import com.heartfelt.connection.prompt.PromptTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * heartfelt_connection 网络通道(v1.2.0 新增——补丁首次引入网络)。
 *
 * 用于告白对话同步:
 * - S2C OpenConfessionPacket:服务端拉客户端打开【女仆主动告白】界面
 * - C2S ConfessionResponsePacket:客户端回传 接受/拒绝(女仆主动告白)
 * - C2S PlayerConfessionPacket(v1.3.0):玩家在 heartfelt 告白屏点"说出心意"
 * - S2C PlayerConfessionResultPacket(v1.3.0):服务端回传女仆回应文本(玩家主动告白)
 */
public final class HeartfeltNetwork {
    /** v1.5.0:删 OpenConfession/ConfessionResponse,新增 OpenMaidMarriageConfession → 协议版本升 3 */
    private static final String PROTOCOL_VERSION = "3";
    private static SimpleChannel channel;

    private HeartfeltNetwork() {
    }

    public static SimpleChannel channel() {
        if (channel == null) {
            channel = NetworkRegistry.newSimpleChannel(
                    new ResourceLocation("heartfelt_connection", "main"),
                    () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
            int id = 0;
            // v1.5.0:女仆主动告白前摇完成 → 跳转 maidmarriage 告白选项界面
            register(id++, OpenMaidMarriageConfessionPacket.class,
                    OpenMaidMarriageConfessionPacket::encode, OpenMaidMarriageConfessionPacket::decode, OpenMaidMarriageConfessionPacket::handle);
            // v1.3.0:玩家主动告白(玩家开口 + 女仆回应)
            register(id++, PlayerConfessionPacket.class,
                    PlayerConfessionPacket::encode, PlayerConfessionPacket::decode, PlayerConfessionPacket::handle);
            register(id++, PlayerConfessionResultPacket.class,
                    PlayerConfessionResultPacket::encode, PlayerConfessionResultPacket::decode, PlayerConfessionResultPacket::handle);
            // v1.4.0:LLM 剧情演绎(alt+J 对话面板按钮)
            register(id++, DramatizePacket.class,
                    DramatizePacket::encode, DramatizePacket::decode, DramatizePacket::handle);
            // v1.4.6:调整器 GUI(S2C 打开/刷新 + C2S 按钮动作)
            register(id++, OpenAdjusterPacket.class,
                    OpenAdjusterPacket::encode, OpenAdjusterPacket::decode, OpenAdjusterPacket::handle);
            register(id++, AdjusterActionPacket.class,
                    AdjusterActionPacket::encode, AdjusterActionPacket::decode, AdjusterActionPacket::handle);
            // v1.5.5:Alt+J 对话面板选择性静止(C2S 冻结/解冻)
            register(id++, ChatFreezePacket.class,
                    ChatFreezePacket::encode, ChatFreezePacket::decode, ChatFreezePacket::handle);
            // v1.5.365:女仆主动告白被拒("……先让我缓缓"=confession_reject)→ 写永久记忆+心情惩罚
            register(id++, ConfessionRejectPacket.class,
                    ConfessionRejectPacket::encode, ConfessionRejectPacket::decode, ConfessionRejectPacket::handle);
            // v1.5.100:手册「立即触发主动表白」调试按钮(C2S,无字段)
            register(id++, ForceConfessionPacket.class,
                    ForceConfessionPacket::encode, ForceConfessionPacket::decode, ForceConfessionPacket::handle);
        }
        return channel;
    }

    private static <T> void register(int id, Class<T> clazz,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        channel.registerMessage(id, clazz, encoder, decoder, handler);
    }

    // ==================== v1.5.0:S2C 跳转 maidmarriage 告白选项界面 ====================

    /**
     * 女仆主动告白前摇完成(走到玩家身边)→ 服务端发本包 → 客户端记住断点
     * (HugStoryResumeState.remember),随后服务端已直调 maidmarriage 的
     * handleInteractionToggle 开启互动(HugActionScreen 打开后消费断点直达
     * confession_intro,世界可见、女仆立绘,非全屏)。
     * v1.5.112:互动开启从客户端 sendHugMaid 往返改为服务端直调(见
     * ConfessionApproachManager)——canSendToServer/反射/C2S 时序任一处静默
     * 失败界面就不弹,是"前摇完成但对话框不出现"的主要嫌疑;本包只负责断点。
     * v1.5.114:maidUuid 允许为 null = 【清除断点】——服务端开启互动失败/
     * 会话中断时下发,防止残留断点把玩家下次正常对话误跳进告白剧本
     * (HugStoryResumeState.pendingResume 是客户端静态量,只有消费没有过期)。
     */
    public static class OpenMaidMarriageConfessionPacket {
        private final UUID maidUuid;

        public OpenMaidMarriageConfessionPacket(UUID maidUuid) {
            this.maidUuid = maidUuid;
        }

        public static void encode(OpenMaidMarriageConfessionPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.maidUuid != null);
            if (packet.maidUuid != null) {
                buf.m_130077_(packet.maidUuid);
            }
        }

        public static OpenMaidMarriageConfessionPacket decode(FriendlyByteBuf buf) {
            boolean has = buf.readBoolean();
            return new OpenMaidMarriageConfessionPacket(has ? buf.m_130259_() : null);
        }

        public static void handle(OpenMaidMarriageConfessionPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                    return;
                }
                Minecraft mc = Minecraft.m_91087_();
                if (mc.f_91073_ == null || mc.f_91074_ == null) {
                    return;
                }
                if (packet.maidUuid == null) {
                    clearMaidMarriageConfessionResume();
                    return;
                }
                if (!jumpToMaidMarriageConfession(packet.maidUuid)) {
                    // v1.5.112:反射失败不再静默——聊天栏提示,玩家/开发者可定位
                    mc.f_91074_.m_213846_(Component.m_237113_(
                            "告白界面跳转失败(maidmarriage 对话系统不可用),请再试一次。"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 清除 maidmarriage 对话断点(remember(null,·,·) 即 pendingResume=null);反射失败静默 */
    private static void clearMaidMarriageConfessionResume() {
        try {
            Class<?> resume = com.heartfelt.connection.compat.ReflectUtil.load(
                    "com.example.maidmarriage.client.dialoguesystem.runtime.HugStoryResumeState");
            java.lang.reflect.Method remember = com.heartfelt.connection.compat.ReflectUtil.method(
                    resume, "remember", UUID.class, net.minecraft.resources.ResourceLocation.class, String.class);
            com.heartfelt.connection.compat.ReflectUtil.invokeStatic(remember, null, null, null);
        } catch (Exception ignored) {
        }
    }

    /** 记录告白断点(客户端);反射失败返回 false(界面会从剧本起点开始而非告白前文) */
    private static boolean jumpToMaidMarriageConfession(UUID maidUuid) {
        try {
            // 断点续播:从告白前文开始(maidmarriage 打开互动屏后自动 jumpToNode)
            // v1.5.363:旧版直达 confession_accept_choice(选项)——女仆主动告白跳过整段
            // 告白前文(confession_intro 女仆的告白台词),"直接到了选项那一步"(用户反馈)。
            // 改为从 confession_intro 开始:完整告白流程(前文 → 开口选项 → 分支 → 接受)正常播放。
            // v1.5.112:不再 sendHugMaid——互动由服务端 ensureInteraction 直调开启,
            // 本方法只负责记住断点。
            Class<?> resume = com.heartfelt.connection.compat.ReflectUtil.load(
                    "com.example.maidmarriage.client.dialoguesystem.runtime.HugStoryResumeState");
            java.lang.reflect.Method remember = com.heartfelt.connection.compat.ReflectUtil.method(
                    resume, "remember", UUID.class, net.minecraft.resources.ResourceLocation.class, String.class);
            if (remember == null) {
                return false;
            }
            com.heartfelt.connection.compat.ReflectUtil.invokeStatic(remember, maidUuid,
                    new net.minecraft.resources.ResourceLocation("maidmarriage", "hug_menu_v2"),
                    "confession_intro");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ==================== C2S:玩家主动告白(说出心意) ====================

    public static class PlayerConfessionPacket {
        private final UUID maidUuid;

        public PlayerConfessionPacket(UUID maidUuid) {
            this.maidUuid = maidUuid;
        }

        public static void encode(PlayerConfessionPacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
        }

        public static PlayerConfessionPacket decode(FriendlyByteBuf buf) {
            return new PlayerConfessionPacket(buf.m_130259_());
        }

        public static void handle(PlayerConfessionPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                for (EntityMaid maid : player.m_9236_().m_45976_(
                        EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
                    if (maid.m_20148_().equals(packet.maidUuid)) {
                        MaidConfessionManager.handlePlayerConfession(player, maid);
                        break;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== S2C:玩家主动告白的结果(女仆回应) ====================

    public static class PlayerConfessionResultPacket {
        public final UUID maidUuid;
        public final boolean accepted;
        public final String responseText;

        public PlayerConfessionResultPacket(UUID maidUuid, boolean accepted, String responseText) {
            this.maidUuid = maidUuid;
            this.accepted = accepted;
            this.responseText = responseText;
        }

        public static void encode(PlayerConfessionResultPacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
            buf.writeBoolean(packet.accepted);
            buf.m_130070_(packet.responseText);
        }

        public static PlayerConfessionResultPacket decode(FriendlyByteBuf buf) {
            return new PlayerConfessionResultPacket(buf.m_130259_(), buf.readBoolean(), buf.m_130277_());
        }

        public static void handle(PlayerConfessionResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                    return;
                }
                com.heartfelt.connection.client.PlayerConfessionScreen.receiveResult(packet);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== C2S:LLM 剧情演绎 ====================

    // ==================== v1.5.365:C2S 女仆主动告白被拒 ====================

    /**
     * 客户端在 maidmarriage 告白剧本选"……先让我缓缓"(choiceId=confession_reject)时
     * 发送——maidmarriage 的拒绝选项无服务端动作,服务端据此写永久记忆(CONFESSION_FAILED)
     * 与心情惩罚(见 MaidConfessionManager.handleConfessionRejected)。
     */
    public static class ConfessionRejectPacket {
        private final UUID maidUuid;

        public ConfessionRejectPacket(UUID maidUuid) {
            this.maidUuid = maidUuid;
        }

        public static void encode(ConfessionRejectPacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
        }

        public static ConfessionRejectPacket decode(FriendlyByteBuf buf) {
            return new ConfessionRejectPacket(buf.m_130259_());
        }

        public static void handle(ConfessionRejectPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                for (EntityMaid maid : player.m_9236_().m_45976_(
                        EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
                    if (maid.m_20148_().equals(packet.maidUuid)) {
                        MaidConfessionManager.handleConfessionRejected(player, maid);
                        break;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class DramatizePacket {
        private final UUID maidUuid;

        public DramatizePacket(UUID maidUuid) {
            this.maidUuid = maidUuid;
        }

        public static void encode(DramatizePacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
        }

        public static DramatizePacket decode(FriendlyByteBuf buf) {
            return new DramatizePacket(buf.m_130259_());
        }

        public static void handle(DramatizePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                for (EntityMaid maid : player.m_9236_().m_45976_(
                        EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
                    if (maid.m_20148_().equals(packet.maidUuid)) {
                        // v1.4.2:LLM 不可用/配额满 → 固定文本提示,不让玩家对着没反应的按钮干等
                        if (!com.heartfelt.connection.dialogue.DialogueDispatcher.dramatize(maid, player)) {
                            player.m_213846_(Component.m_237113_(PromptTexts.dramatizeUnavailable()));
                        }
                        break;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== v1.4.6:调整器 GUI ====================

    /**
     * S2C:打开/刷新调整器界面。
     * 服务端组装实时状态快照(状态行 + 最近操作结果)下发;客户端已打开
     * 同一女仆的界面则原地刷新,否则打开新界面(AdjusterScreen)。
     */
    public static class OpenAdjusterPacket {
        public final UUID maidUuid;
        public final String maidName;
        public final List<String> statusLines;
        /** 最近一次操作结果文案(v1.4.10,GUI 黄色结果行);可空 */
        public final String result;

        public OpenAdjusterPacket(UUID maidUuid, String maidName, List<String> statusLines, String result) {
            this.maidUuid = maidUuid;
            this.maidName = maidName;
            this.statusLines = statusLines;
            this.result = result;
        }

        public static void encode(OpenAdjusterPacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
            buf.m_130070_(packet.maidName);
            buf.writeInt(packet.statusLines.size()); // Netty writeInt(不混淆)
            for (String line : packet.statusLines) {
                buf.m_130070_(line);
            }
            buf.writeBoolean(packet.result != null);
            if (packet.result != null) {
                buf.m_130070_(packet.result);
            }
        }

        public static OpenAdjusterPacket decode(FriendlyByteBuf buf) {
            UUID maidUuid = buf.m_130259_();
            String maidName = buf.m_130277_();
            int size = Math.max(0, Math.min(buf.readInt(), 16)); // Netty readInt
            List<String> lines = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                lines.add(buf.m_130277_());
            }
            String result = buf.readBoolean() ? buf.m_130277_() : null;
            return new OpenAdjusterPacket(maidUuid, maidName, lines, result);
        }

        public static void handle(OpenAdjusterPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                    return;
                }
                Minecraft mc = Minecraft.m_91087_();
                if (mc.f_91073_ == null || mc.f_91074_ == null) {
                    return;
                }
                if (mc.f_91080_ instanceof AdjusterScreen screen && screen.sameMaid(packet.maidUuid)) {
                    screen.refresh(packet); // 同一女仆:原地刷新状态
                } else {
                    mc.m_91152_(new AdjusterScreen(packet));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** C2S:调整器按钮动作(maidUuid + action,action 与 AdjusterManager.applyAction 对齐) */
    public static class AdjusterActionPacket {
        private final UUID maidUuid;
        private final String action;

        public AdjusterActionPacket(UUID maidUuid, String action) {
            this.maidUuid = maidUuid;
            this.action = action;
        }

        public static void encode(AdjusterActionPacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
            buf.m_130070_(packet.action);
        }

        public static AdjusterActionPacket decode(FriendlyByteBuf buf) {
            return new AdjusterActionPacket(buf.m_130259_(), buf.m_130277_());
        }

        public static void handle(AdjusterActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                // v1.5.7:close = 关闭调整器界面,恢复选择性静止(不依赖找到女仆)
                if ("close".equals(packet.action)) {
                    com.heartfelt.connection.dialogue.DialogueFreezeManager.stopFreeze(player);
                    return;
                }
                for (EntityMaid maid : player.m_9236_().m_45976_(
                        EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
                    if (!maid.m_20148_().equals(packet.maidUuid)) {
                        continue;
                    }
                    // 审计修复(1.5.115)：调整器动作必须手持调整器（或 OP）——
                    // 旧版网络路径无校验，64 格内任意玩家可改任意女仆好感/心情/信任
                    // （合法入口 AdjusterInteractHandler 有手持校验，网络包绕过了它）
                    if (player.m_21205_().m_41720_() != com.heartfelt.connection.item.HeartfeltItems.ADJUSTER.get()
                            && !player.m_20310_(2)) {
                        player.m_213846_(Component.m_237113_(
                                "\u00a7c需要手持调整器才能操作女仆状态。"));
                        break;
                    }
                    // v1.4.10:applyAction 返回结果文案 → 非 null 才刷新(带结果反馈)
                    String result = AdjusterManager.applyAction(player, maid, packet.action);
                    if (result != null) {
                        AdjusterManager.openGui(player, maid, result);
                    }
                    break;
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== v1.5.5:Alt+J 对话面板选择性静止 ====================

    /**
     * C2S:Alt+J 对话面板(AIChatScreen)打开/关闭时通知服务端——
     * 打开 → 选择性静止(其他生物定住,玩家与对话目标女仆可动);
     * 关闭 → 恢复。用户需求:时间暂停只挂在 LLM 对话上,其他场合不触发。
     */
    public static class ChatFreezePacket {
        private final UUID maidUuid;
        private final boolean freeze;

        public ChatFreezePacket(UUID maidUuid, boolean freeze) {
            this.maidUuid = maidUuid;
            this.freeze = freeze;
        }

        public static void encode(ChatFreezePacket packet, FriendlyByteBuf buf) {
            buf.m_130077_(packet.maidUuid);
            buf.writeBoolean(packet.freeze);
        }

        public static ChatFreezePacket decode(FriendlyByteBuf buf) {
            return new ChatFreezePacket(buf.m_130259_(), buf.readBoolean());
        }

        public static void handle(ChatFreezePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                if (packet.freeze) {
                    // 打开:冻结(非玩家、非目标女仆的生物静止)
                    for (EntityMaid maid : player.m_9236_().m_45976_(
                            EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
                        if (maid.m_20148_().equals(packet.maidUuid)) {
                            com.heartfelt.connection.dialogue.DialogueFreezeManager.startFreeze(player, maid);
                            break;
                        }
                    }
                } else {
                    // 关闭:恢复
                    com.heartfelt.connection.dialogue.DialogueFreezeManager.stopFreeze(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== v1.5.100:手册「立即触发主动表白」 ====================

    /**
     * C2S:玩家在 Promaid 手册点「立即触发主动表白」——服务端对发送者调用
     * MaidConfessionManager.forceConfession(找附近好感最高的资格女仆,跳过
     * 概率/冷却立即启动告白前摇),结果文案系统消息反馈。
     */
    public static class ForceConfessionPacket {
        public ForceConfessionPacket() {
        }

        public static void encode(ForceConfessionPacket packet, FriendlyByteBuf buf) {
        }

        public static ForceConfessionPacket decode(FriendlyByteBuf buf) {
            return new ForceConfessionPacket();
        }

        public static void handle(ForceConfessionPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                    return;
                }
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                String result = com.heartfelt.connection.dialogue.MaidConfessionManager.forceConfession(player);
                if (result != null) {
                    player.m_213846_(Component.m_237113_(result));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
