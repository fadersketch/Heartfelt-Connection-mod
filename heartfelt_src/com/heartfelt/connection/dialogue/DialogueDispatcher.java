package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.ReflectUtil;
import com.heartfelt.connection.quota.ApiQuotaBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话公共分发器(v1.1.0 全面重构)。
 *
 * 三个对话管理器(广播/家庭互动/哀悼)的公共流程收敛点:
 * - 主人判定(A3 修复:UUID 比较代替实体引用 !=,跨维度/重进不再失效)
 * - 女仆集合扫描(驯服+存活+属于该玩家)
 * - 配额内对话触发(统一走 ApiQuotaBridge)
 */
public final class DialogueDispatcher {
    private DialogueDispatcher() {
    }

    /** 女仆是否属于该玩家(A3:UUID 比较) */
    public static boolean isOwner(EntityMaid maid, Player player) {
        if (maid == null || player == null) {
            return false;
        }
        LivingEntity owner = maid.m_269323_();
        return owner != null && owner.m_20148_().equals(player.m_20148_());
    }

    /** 两女仆是否同主人(A3) */
    public static boolean sameOwner(EntityMaid a, EntityMaid b) {
        LivingEntity oa = a.m_269323_();
        LivingEntity ob = b.m_269323_();
        return oa != null && ob != null && oa.m_20148_().equals(ob.m_20148_());
    }

    /** 玩家周围 range 格内,已驯服、存活、属于该玩家的女仆列表(每调用重新扫描) */
    public static List<EntityMaid> maidsOf(ServerPlayer player, int range) {
        List<EntityMaid> all = player.m_9236_().m_45976_(
                EntityMaid.class, player.m_20191_().m_82400_(range));
        List<EntityMaid> result = new ArrayList<>();
        for (EntityMaid maid : all) {
            if (maid.m_21824_() && maid.m_6084_() && isOwner(maid, player)) {
                result.add(maid);
            }
        }
        return result;
    }

    /**
     * LLM 是否真正可用(v1.4.2)——与 TLM `MaidAIChatManager.chat()` 内部拦截链一致:
     * 1. TLM 总开关 LLM_ENABLED
     * 2. 当前站点存在且启用(反射 public getLLMSite——定义在父类 MaidAIChatData,
     *    未配置时默认返回 DeepSeek 站点,enabled=true)
     * 3. DeepSeek 未填 SecretKey → TLM 只发提示不实际对话,同样视为不可用
     *
     * 用于"无 LLM 完整运作"降级:不可用时调用方改发固定文本气泡,
     * 玩家不会看到 TLM 的红色报错提示。
     */
    public static boolean isLLMReady(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        if (!AIConfig.LLM_ENABLED.get()) {
            return false;
        }
        Object manager = maid.getAiChatManager();
        if (manager == null) {
            return false;
        }
        Method getSite = ReflectUtil.method(manager.getClass(), "getLLMSite");
        Object site = ReflectUtil.invoke(getSite, manager);
        if (site == null) {
            return false;
        }
        Method enabled = ReflectUtil.method(site.getClass(), "enabled");
        if (!Boolean.TRUE.equals(ReflectUtil.invoke(enabled, site))) {
            return false;
        }
        Method id = ReflectUtil.method(site.getClass(), "id");
        if ("deepseek".equals(ReflectUtil.invoke(id, site))) {
            Method secretKey = ReflectUtil.method(site.getClass(), "secretKey");
            Object key = ReflectUtil.invoke(secretKey, site);
            if (key == null || String.valueOf(key).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** 配额内触发女仆 LLM 对话;配额满(或异常)返回 false */
    public static boolean chatWithQuota(EntityMaid maid, ServerPlayer player, String prompt) {
        return chatWithQuota(maid, player, prompt, null);
    }

    /**
     * 安全区检查(v1.5.8,女仆主动告白前摇 / Alt+J 对话面板共用):
     * 玩家与女仆周围 radius 内都没有敌对生物(Monster)、女仆不在战斗中,
     * 才算"可以安心进行这些举动"——原版没有,需先打掉敌人才能对话/告白。
     */
    public static boolean isSafeArea(ServerPlayer player, EntityMaid maid, double radius) {
        if (player == null || maid == null || !maid.m_6084_()) {
            return false;
        }
        if (maid.m_5448_() != null) {
            return false; // 女仆正在战斗
        }
        if (!maid.m_9236_().m_45976_(Monster.class, maid.m_20191_().m_82400_(radius)).isEmpty()) {
            return false; // 女仆周围有敌人
        }
        return player.m_9236_().m_45976_(
                Monster.class, player.m_20191_().m_82400_(radius)).isEmpty(); // 玩家周围也不能有敌人
    }

    /**
     * 配额内触发女仆 LLM 对话(v1.4.2 降级兜底)。
     *
     * 无 LLM / 配额满 → 发固定文本气泡(fallbackBubble,非空时)并返回 false——
     * 玩家看到"她在说话"而不是 TLM 的红色报错;已发气泡即视为已触发,调用方照常去重。
     */
    public static boolean chatWithQuota(EntityMaid maid, ServerPlayer player, String prompt,
            String fallbackBubble) {
        if (maid == null || player == null) {
            return false;
        }
        // v1.5.31：幼儿女儿（INFANT/JUVENILE）不产生任何主动话语——她是婴儿，不会说话
        // （父女/母女互动、纪念日、成长、广播、暗恋感慨等一切以她为说话者的场景在此兜底拦截；
        // 玩家主动与她聊天不受影响——那是 TLM chat 入口，不走本方法）
        if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
            return false;
        }
        // v1.5.30：promaid per-maid LLM 开关（persistentData "maid_smart_llm"，
        // 无 = 默认开）——关闭时主动对话直接降级为固定文本气泡，不发 LLM 请求
        // （零跨 mod 依赖：只读 NBT 标记；TLM 原版聊天由 promaid mixin 拦截）
        if (!isLLMReady(maid) || !ApiQuotaBridge.tryAcquire()
                || !maidLlmEnabled(maid)) {
            if (fallbackBubble != null && !fallbackBubble.isBlank()) {
                maid.getChatBubbleManager().addTextChatBubble(fallbackBubble);
            }
            return false;
        }
        try {
            maid.getAiChatManager().chat(prompt, ChatClientInfo.fromMaid(maid), player);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** v1.5.30：promaid per-maid LLM 开关（"maid_smart_llm" Byte；无标记 = 默认开） */
    private static boolean maidLlmEnabled(EntityMaid maid) {
        try {
            net.minecraft.nbt.CompoundTag pd = maid.getPersistentData();
            if (pd.m_128425_("maid_smart_llm", 1)) {
                return pd.m_128435_("maid_smart_llm") != 0;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    /**
     * v1.4.0:LLM 剧情演绎(alt+J 对话面板按钮触发)。
     *
     * 脱离 maidmarriage 的固定剧本:根据 heartfelt 的事件历史(P1 Shared History)
     * 生成一段 galgame 式剧情提示词,让女仆用 LLM 沉浸式演绎。走全局 API 配额。
     */
    public static boolean dramatize(EntityMaid maid, ServerPlayer player) {
        if (maid == null || player == null || !isOwner(maid, player)) {
            return false;
        }
        if (!ApiQuotaBridge.tryAcquire()) {
            return false;
        }
        String history = com.heartfelt.connection.memory.EventHistoryManager.buildHistoryText(maid);
        String prompt = com.heartfelt.connection.prompt.PromptTexts.dramatizeSystemPrompt(history);
        try {
            maid.getAiChatManager().chat(prompt, ChatClientInfo.fromMaid(maid), player);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
