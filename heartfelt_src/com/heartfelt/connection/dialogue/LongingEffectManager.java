package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 思慕明显效果(v1.5.346)：用户反馈"思慕效果要么触发不了、要么太不明显"。
 *
 * 背景（反编译 maidmarriage 2.3.0 实证）：maidmarriage 自带的思慕呈现太弱——
 * onMaidTick 每秒只在女仆身边撒 2 颗心形粒子（m_8767_ count=2）加低频循环对话，
 * 玩家几乎感知不到；且 isLongingForInteraction 有 isDatingOrMarried 前置，
 * 女儿（儿童）永远不会进入恋爱向思慕——heartfelt v1.5.57 准备的三档女儿思慕文本
 * （BABY/CHILD/ADULT_LONGING）因此一直是死代码。
 *
 * 本管理器在 heartfelt 侧每秒扫描，对【思慕中】的关系女仆（主人 24 格内）给足反馈：
 *  1. 心形粒子爆发：每秒 8 颗（maidmarriage 的 4 倍，明显可见）
 *  2. 思慕气泡：每 30 秒一句（妻子/恋人/女儿分文案；女儿按成长阶段轮换 LONGING 池）
 *  3. 系统消息：每游戏日一次，明确"她在思念你"（触发看得见）
 *
 * 触发判定：
 *  - 妻子/恋人：maidmarriage MaidMoodManager.isLongingForInteraction（恋爱/结婚 +
 *    lastInteractionDay 距今 ≥3 天）
 *  - 女儿：同样的 lastInteractionDay ≥3 天（父女思念，用女儿文案，不落恋爱向）
 * 边界：伤心窗口/哀悼中不触发（她正赌气/难过，不冒心形）；主人不在附近不触发。
 */
public final class LongingEffectManager {

    /** 思慕判定天数（与 maidmarriage LONGING_TRIGGER_DAYS=3 一致） */
    private static final long LONGING_DAYS = 3L;
    /** 思慕气泡冷却（游戏刻，30 秒） */
    private static final long BUBBLE_COOLDOWN_TICKS = 600L;
    /** 效果触发距离（格） */
    private static final double EFFECT_RANGE = 24.0;
    /** 每秒 8 颗心形粒子 */
    private static final int HEART_COUNT = 8;

    private final Map<UUID, Long> bubbleUntil = new ConcurrentHashMap<>();

    /** 审计 H-M1：女仆卸载/移除时清理思慕气泡冷却表 */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        this.bubbleUntil.remove(maid.m_20148_());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.m_129921_() % 20 != 0) {
            return;
        }
        long now = server.m_129921_();
        long day = now / 24000L;
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                try {
                    // 主人太远看不到，不浪费。m_20280_ 返回【平方距离】，
                    // 必须与 EFFECT_RANGE^2 比较(旧版与 24.0 直接比,实际约 4.9 格
                    // 就判"太远",思慕效果几乎只在贴身才触发)。
                    // v1.5.114:距离过滤前置(便宜)——旧版先做 longingActive(反射
                    // +任务数据读取),24~48 格的女仆每秒白白做一次昂贵判定
                    if (maid.m_20280_(player) > EFFECT_RANGE * EFFECT_RANGE) {
                        continue;
                    }
                    if (!longingActive(maid)) {
                        continue;
                    }
                    if (FamilyInteractionManager.isHurtFeeling(maid)) {
                        continue;
                    }
                    // 1. 心形粒子爆发
                    if (maid.m_9236_() instanceof ServerLevel sl) {
                        sl.m_8767_(ParticleTypes.f_123750_ /* HEART */,
                                maid.m_20185_(), maid.m_20227_(1.0), maid.m_20189_(),
                                HEART_COUNT, 0.4, 0.35, 0.4, 0.02);
                    }
                    // 2. 思慕气泡（冷却;v1.5.96 过主动说话仲裁——别的女仆正在
                    // 说(关系互动/玩家回应)时让位,避免多女仆同时冒泡）
                    Long until = this.bubbleUntil.get(maid.m_20148_());
                    if (until == null || until <= now) {
                        if (DialogueArbiter.trySpeak(maid, player.m_20148_(),
                                DialogueArbiter.Channel.AMBIENT)) {
                            this.bubbleUntil.put(maid.m_20148_(), now + BUBBLE_COOLDOWN_TICKS);
                            maid.getChatBubbleManager().addTextChatBubble(
                                    PromptTexts.longingBubble(maid));
                        }
                    }
                    // 3. 系统消息（每游戏日一次，触发看得见）
                    long lastMsgDay = maid.getPersistentData().m_128454_(
                            HeartfeltTags.LAST_LONGING_MSG_DAY);
                    if (lastMsgDay != day) {
                        maid.getPersistentData().m_128356_(
                                HeartfeltTags.LAST_LONGING_MSG_DAY, day);
                        player.m_213846_(Component.m_237113_(
                                PromptTexts.longingEffectMessage(
                                        maid.m_7755_().getString(),
                                        RelationshipExemption.relationLabel(maid))));
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 思慕判定：女儿按父女思念（lastInteractionDay≥3 天），其余走 maidmarriage 状态 */
    private static boolean longingActive(EntityMaid maid) {
        if (RelationshipExemption.isChild(maid)) {
            Object data = MaidMarriageCompat.readTaskData(maid, "mood_data");
            Long last = MaidMarriageCompat.readLong(data, "lastInteractionDay");
            if (last == null || last < 0L) {
                return false;
            }
            long today = maid.m_9236_().m_46467_() / 24000L;
            return today - last >= LONGING_DAYS;
        }
        return MaidMarriageCompat.isLongingForInteraction(maid);
    }
}
