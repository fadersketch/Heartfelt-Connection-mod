package com.heartfelt.connection.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * v1.5.53:少女学习/探险归来汇报 + 爸爸受伤时女儿关心。
 *
 * ① 归来汇报:maidmarriage 的学习/探险任务完成时只有系统消息(message.child.learn.finish
 *   /explore.finish),没有女儿口吻的气泡台词——周期检测 persistent 的
 *   maidmarriage_child_action_mode(学习/探险模式,完成时清除)从非空变空的瞬间,
 *   发少女归来气泡(按天轮换池,同 tick 只一次,任务中途取消也会发——文本用
 *   泛指"回来啦"语义,可接受);
 * ② 受伤关心:玩家(爸爸)受伤瞬间,48 格内关系女儿按阶段发关心气泡——幼女
 *   (旁白,看到爸爸受伤大哭)、少女(吓一跳)、成女(照顾),每女儿 5 分钟冷却。
 *
 * 只读 persistentData 字符串 + 标准 Forge 事件,零跨 mod 依赖(与 ChildGuardManager
 * 同模式,注册于 HeartfeltExtension)。
 */
public final class ChildActionReportManager {

    /** 审计 H-M3：女仆卸载/移除时清理任务模式与受伤关心表 */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        java.util.UUID id = maid.m_20148_();
        LAST_ACTION_MODE.remove(id);
        HURT_WATCH_AT.remove(id);
    }

    /** maidmarriage 学习/探险任务模式 key(完成/取消时清除) */
    private static final String ACTION_MODE = "maidmarriage_child_action_mode";

    /** maid uuid -> 上轮任务模式("" 或 null=空闲) */
    private static final java.util.Map<UUID, String> LAST_ACTION_MODE = new java.util.concurrent.ConcurrentHashMap<>();
    /** maid uuid -> 上次受伤关心时刻(tick),5 分钟冷却 */
    private static final java.util.Map<UUID, Long> HURT_WATCH_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long HURT_WATCH_COOLDOWN = 6000L;

    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null || server.m_129921_() % 5 != 0) {
            return;
        }
        long day = server.m_129921_() / 24000L;
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                // 学习/探险是少女(CHILD)专属任务;幼女任务锁空闲、成女不学习
                if (!RelationshipExemption.isChild(maid) || ChildGuardManager.isTooSmall(maid)) {
                    continue;
                }
                if (MaidMarriageCompat.childStage(maid) != MaidMarriageCompat.ChildStage.CHILD) {
                    continue;
                }
                UUID id = maid.m_20148_();
                String cur = maid.getPersistentData().m_128461_(ACTION_MODE);
                if (cur == null || cur.isEmpty()) {
                    String prev = LAST_ACTION_MODE.put(id, "");
                    if (prev != null && !prev.isEmpty()) {
                        // 任务结束瞬间 → 归来汇报(学习/探险,按天轮换;v1.5.96 过仲裁——
                        // 别的女仆正在主动说话时让位,女儿晚点再说)
                        if (com.heartfelt.connection.dialogue.DialogueArbiter.trySpeak(
                                maid, player.m_20148_(),
                                com.heartfelt.connection.dialogue.DialogueArbiter.Channel.INTIMATE)) {
                            maid.getChatBubbleManager().addTextChatBubble(
                                    PromptTexts.childActionReport(prev.contains("explore"), maid, day));
                        }
                    }
                } else {
                    LAST_ACTION_MODE.put(id, cur);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        // Forge 1.20.1 无 PlayerHurtEvent——玩家受伤走 LivingHurtEvent,判断实体
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.m_9236_().f_46443_) {
            return; // 服务端
        }
        long day = player.m_9236_().m_46467_() / 24000L;
        for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
            if (!RelationshipExemption.isChild(maid) || !maid.m_6084_()) {
                continue;
            }
            UUID id = maid.m_20148_();
            Long last = HURT_WATCH_AT.get(id);
            if (last != null && player.m_9236_().m_46467_() - last < HURT_WATCH_COOLDOWN) {
                continue;
            }
            HURT_WATCH_AT.put(id, player.m_9236_().m_46467_());
            maid.getChatBubbleManager().addTextChatBubble(
                    PromptTexts.hurtWatchText(ChildGuardManager.isTooSmall(maid), maid, day));
        }
    }
}
