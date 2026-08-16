package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 主人死亡反馈(v1.1.0;v1.5.6 设计修正:死亡调侃替代死亡哀悼;
 *  v1.5.108 彻底移除哀悼;v1.5.111 真死核验)。
 *
 * 历史:
 * - 原设计:主人倒下时关系女仆进入 1 游戏日【哀悼期】——打标记、触发悲痛
 *   LLM 对话、拒绝亲密互动。
 * - v1.5.6:玩家会复活、女仆会传送重生点——"哀悼拒绝互动"与复活机制矛盾,
 *   改为死亡瞬间一次【调侃/关心】反馈(按关系分档文本 + 气泡),无状态窗口。
 * - v1.5.108:哀悼作为被替代的旧设计彻底删除——applyMourning/到期扫描/
 *   调整器入口/各读取分支全部移除,只保留死亡调侃。
 *
 * 死亡调侃触发(用户反馈此前从未触发,疑似死亡瞬间消息被吞):
 * - 事件内仅【收集候选】,推迟到下一服务端 tick 结束再发送,玩家聊天栏稳定接收;
 * - v1.5.111:发送前核验【真死】——不死图腾/复活类机制救回(玩家实体未被
 *   移除且血量>0)一律不调侃,杜绝"主人其实没死、女仆却以为主人死了"的
 *   误判(此前死亡传送机制就有不死图腾误判先例);
 * - 只取【离玩家最近的 5 只】关系女仆(按距离排序,其余不出声,防刷屏);
 * - 婴儿/幼儿(tooSmall)不调侃(她们不会说话,大哭由伤害系统处理),
 *   少女/成女/妻子照常。
 */
public class FamilyMourningManager {

    /** 死亡调侃最多触发的关系女仆数 */
    private static final int MAX_TEASE_MAIDS = 5;
    /** 死亡调侃候选队列:死亡瞬间收集,下一 tick 核验真死后发送 */
    private static final java.util.ArrayDeque<PendingDeathTease> PENDING = new java.util.ArrayDeque<>();

    @SubscribeEvent
    public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // v1.5.111:入口即核验濒死——真死路径上血量必然已 ≤0(isDeadOrDying),
        // 非濒死状态的死亡事件(其他模组手发/复活类流程)直接忽略
        if (!player.m_21224_()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.m_9236_();
        // v1.5.110:死亡调侃只取【离玩家最近的 5 只】关系女仆——附近关系女仆
        // 全触发会刷屏(妻子/恋人/女儿各自发一条);按距离排序取前 5,其余不出声。
        java.util.List<EntityMaid> nearby = new java.util.ArrayList<>();
        for (EntityMaid maid : level.m_45976_(EntityMaid.class, player.m_20191_().m_82400_(64.0))) {
            if (!maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                continue; // A3:UUID 比较
            }
            if (!RelationshipExemption.isFrozen(maid)) {
                continue;
            }
            // v1.5.68:婴儿/幼儿不会说话——死亡调侃(按关系分档的台词)跳过,
            // 她们只剩旁白级反馈(哇哇大哭由伤害系统处理);少女/成女/妻子照常
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
                continue;
            }
            nearby.add(maid);
        }
        nearby.sort(java.util.Comparator.comparingDouble(m -> m.m_20280_(player)));
        int count = Math.min(MAX_TEASE_MAIDS, nearby.size());
        // 玩家死亡瞬间收集候选(此时玩家还在原位,距离排序有效),
        // 下一 tick 结束核验真死后才真正发送消息/气泡
        PENDING.add(new PendingDeathTease(player, level, new java.util.ArrayList<>(nearby.subList(0, count)),
                level.m_46467_() + 1));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (!PENDING.isEmpty()) {            PendingDeathTease pending = PENDING.peek();
            ServerLevel level = pending.level();
            if (level == null || level.m_46467_() < pending.triggerTick()) {
                break; // 时间未到;队列按触发 tick 递增,直接退出
            }
            PENDING.poll();
            ServerPlayer player = pending.player();
            // v1.5.111:真死核验——不死图腾/复活类机制救回时玩家实体未被移除
            // 且血量 >0,此时不调侃;真死(被击杀移除/血量归零)才触发
            if (!player.m_213877_() && player.m_21223_() > 0.0F) {
                continue;
            }
            for (EntityMaid maid : pending.maids()) {
                if (maid.m_213877_() || !maid.m_6084_()) {
                    continue;
                }
                player.m_213846_(Component.m_237113_(PromptTexts.deathTeaseMessage(
                        maid.m_7755_().getString(), RelationshipExemption.relationLabel(maid))));
                // v1.5.39:死亡调侃气泡按关系称呼
                maid.getChatBubbleManager().addTextChatBubble(
                        PromptTexts.deathTeaseBubble(PromptTexts.termOfAddress(maid)));
            }
        }
    }

    /** v1.5.114:停服清空候选队列——防跨重启处理陈旧死亡条目(旧玩家实体引用) */
    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        PENDING.clear();
    }

    private record PendingDeathTease(ServerPlayer player, ServerLevel level,
                                     java.util.List<EntityMaid> maids, long triggerTick) {
    }
}
