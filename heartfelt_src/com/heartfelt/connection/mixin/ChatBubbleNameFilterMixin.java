package com.heartfelt.connection.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.prompt.ChatNameFilter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 气泡称呼换字(v1.5.42)——ChatBubbleManager 是女仆气泡【统一出口】
 * (heartfelt / maidmarriage 剧本 / TLM 全部经过),在此按女仆关系把
 * 「主人」替换为 亲爱的(恋人)/丈夫(妻子)/爸爸(女儿)——所有气泡全覆盖,
 * 无需逐处改文本。
 *
 * 覆盖:addTextChatBubble / addTextChatBubbleIfTimeout / addThinkingText /
 * addLLMChatText(String 版本) + addChatBubble(IChatBubbleData,底层——
 * TextChatBubbleData 用 setText 重写)。
 *
 * TLM 类在编译 classpath → 普通 @Mixin(两端加载,气泡管理两端同构)。
 */
@Mixin(ChatBubbleManager.class)
public abstract class ChatBubbleNameFilterMixin {

    @Shadow
    private EntityMaid maid;

    @WrapMethod(method = "addTextChatBubble")
    private long heartfelt$filterTextBubble(String text, Operation<Long> original) {
        return original.call(ChatNameFilter.replaceFor(this.maid, text));
    }

    @WrapMethod(method = "addTextChatBubbleIfTimeout")
    private long heartfelt$filterTextBubbleTimeout(String text, long timeout, Operation<Long> original) {
        return original.call(ChatNameFilter.replaceFor(this.maid, text), timeout);
    }

    @WrapMethod(method = "addThinkingText")
    private long heartfelt$filterThinking(String text, Operation<Long> original) {
        return original.call(ChatNameFilter.replaceFor(this.maid, text));
    }

    @WrapMethod(method = "addLLMChatText")
    private void heartfelt$filterLlmChat(String text, long timeout, Operation<Void> original) {
        original.call(ChatNameFilter.replaceFor(this.maid, text), timeout);
    }

    /** 底层气泡(maidmarriage 剧本走这里):TextChatBubbleData 用 setText 重写 */
    @WrapMethod(method = "addChatBubble")
    private long heartfelt$filterDataBubble(IChatBubbleData data, Operation<Long> original) {
        if (data instanceof TextChatBubbleData td) {
            try {
                java.lang.reflect.Field f = TextChatBubbleData.class.getDeclaredField("text");
                f.setAccessible(true);
                Component c = (Component) f.get(td);
                String s = c == null ? "" : c.getString();
                String filtered = ChatNameFilter.replaceFor(this.maid, s);
                if (!filtered.equals(s)) {
                    td.setText(Component.m_237113_(filtered));
                }
            } catch (Exception ignored) {
            }
        }
        return original.call(data);
    }
}
