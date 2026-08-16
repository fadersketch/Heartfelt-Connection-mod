package com.heartfelt.connection.prompt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 称呼换字(v1.5.42)——"换字游戏":女仆发出的所有系统消息/气泡里,
 * 「主人」按她的关系统一替换——恋人=亲爱的、妻子=丈夫、女儿=爸爸;
 * 普通女仆维持「主人」。
 *
 * 统一出口替换:气泡由 ChatBubbleNameFilterMixin 在 ChatBubbleManager 层全覆盖
 * (heartfelt/maidmarriage/TLM 一切气泡);系统消息由各发送点改用 sendTo。
 */
public final class ChatNameFilter {

    private ChatNameFilter() {
    }

    /** 按女仆关系替换文本中的「主人」;无「主人」字样或普通女仆原样返回 */
    public static String replaceFor(EntityMaid maid, String text) {
        if (text == null || text.isEmpty() || !text.contains("主人") || maid == null) {
            return text;
        }
        String addr = PromptTexts.termOfAddress(maid);
        if ("主人".equals(addr)) {
            return text;
        }
        return text.replace("主人", addr);
    }

    /** 系统消息统一发送(玩家视角;按说话者女仆关系换字) */
    public static void sendTo(ServerPlayer player, EntityMaid speaker, String text) {
        if (player == null || text == null) {
            return;
        }
        player.m_213846_(Component.m_237113_(replaceFor(speaker, text)));
    }
}
