package com.heartfelt.connection.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.item.AdjusterManager;
import com.heartfelt.connection.item.HeartfeltItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * /heartfelt 命令(v1.4.3)——调整器按钮的执行通道。
 *
 * 结构:/heartfelt adjust <uuid> <action>
 * 守卫:必须手持调整器(物品即权限,不设 op 限制——拿不到物品就无法使用);
 * 64 格内按 UUID 找女仆(与全库一致的防御性解析)。
 *
 * 点击聊天栏按钮(run_command)→ 命令执行 → applyAction 调整 → 自动重发菜单刷新数值。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class HeartfeltCommand {
    private HeartfeltCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.m_82127_("heartfelt")
                .then(Commands.m_82127_("adjust")
                        .then(Commands.m_82129_("uuid", StringArgumentType.string())
                                .then(Commands.m_82129_("action", StringArgumentType.string())
                                        .executes(ctx -> execute(
                                                ctx.getSource().m_81375_(),
                                                StringArgumentType.getString(ctx, "uuid"),
                                                StringArgumentType.getString(ctx, "action")))))));
    }

    private static int execute(ServerPlayer player, String uuidStr, String action) {
        // 守卫:必须手持调整器(物品即权限)
        if (!player.m_21205_().m_41720_().equals(HeartfeltItems.ADJUSTER.get())) {
            player.m_213846_(Component.m_237113_("\u00a7c请手持调整器使用此命令"));
            return 0;
        }
        UUID maidId;
        try {
            maidId = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            player.m_213846_(Component.m_237113_("\u00a7c无效的女仆 ID"));
            return 0;
        }
        // 64 格内找女仆
        for (EntityMaid maid : player.m_9236_().m_45976_(
                EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
            if (!maid.m_20148_().equals(maidId)) {
                continue;
            }
            // v1.4.10:applyAction 返回结果文案 → 非 null 才刷新 GUI(带结果反馈)
            String result = AdjusterManager.applyAction(player, maid, action);
            if (result != null) {
                AdjusterManager.openGui(player, maid, result);
            }
            return 1;
        }
        player.m_213846_(Component.m_237113_("\u00a7c附近找不到该女仆"));
        return 0;
    }
}
