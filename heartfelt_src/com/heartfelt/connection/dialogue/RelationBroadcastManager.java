package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关系传播(v1.5.26;v1.1.0 A6 判空 + A7 快照清理 + A3 UUID 比较 + 配置间隔)。
 *
 * 女仆与主人结成关系(恋人/妻子/女儿)后,**其他女仆会知道这件事**并主动调用
 * LLM 对话(祝福/感慨/调侃)。
 *
 * 机制:
 * - 每 N tick 扫描所有玩家的女仆,统计"关系女仆"集合(RelationshipExemption)
 * - 与上次快照对比:出现【新增】关系女仆 → 对该玩家其他女仆各触发一次对话
 * - 每只女仆每个游戏日最多广播 1 次(防刷);纳入全局 API 配额
 *
 * v1.1.0:
 * - A6:server 判空(与同包其他管理器一致,防服务器起停边界 NPE)
 * - A7:lastSnapshot 随日切换清理——女仆死亡/解雇后不再留存内存
 * - A3:主人判定走 DialogueDispatcher.isOwner(UUID 比较)
 * - 扫描间隔/文案集中(HeartfeltConfig/PromptTexts)
 */
public class RelationBroadcastManager {
    /** player UUID -> (maid UUID -> 关系标签) 上次快照 */
    private final Map<UUID, Map<UUID, String>> lastSnapshot = new ConcurrentHashMap<>();
    /** maid UUID -> 最后广播的游戏日(每女仆每天 1 次) */
    private final Map<UUID, Long> lastBroadcastDay = new ConcurrentHashMap<>();
    private long lastDay = -1;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!AIConfig.LLM_ENABLED.get()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return; // A6:服务器起停边界判空
        }
        long tick = server.m_129921_();
        if (tick % HeartfeltConfig.BROADCAST_SCAN_INTERVAL.get() != 0) {
            return;
        }
        long day = tick / 24000L;
        if (day != this.lastDay) {
            this.lastDay = day;
            this.lastBroadcastDay.clear();
            // A7:快照只保留当天仍活跃的玩家条目(清掉女仆死亡/解雇后的残留)
            this.lastSnapshot.entrySet().removeIf(e ->
                    server.m_6846_().m_11314_().stream().noneMatch(p -> p.m_20148_().equals(e.getKey())));
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            Map<UUID, String> current = new HashMap<>();
            for (EntityMaid maid : player.m_9236_().m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0))) {
                if (!maid.m_21824_() || !maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                    continue; // A3:UUID 比较
                }
                String label = RelationshipExemption.relationLabel(maid);
                if (label != null) {
                    current.put(maid.m_20148_(), label);
                }
            }
            Map<UUID, String> prev = this.lastSnapshot.getOrDefault(player.m_20148_(), Map.of());
            for (Map.Entry<UUID, String> entry : current.entrySet()) {
                if (prev.containsKey(entry.getKey())) {
                    continue; // 之前已有该关系,不重复广播
                }
                this.broadcastToOtherMaids(player, entry.getKey(), entry.getValue(), day);
            }
            this.lastSnapshot.put(player.m_20148_(), current);
        }
    }

    /** 让玩家名下其他女仆得知"某位女仆与主人结成关系"(各触发一次 LLM 对话) */
    private void broadcastToOtherMaids(ServerPlayer player, UUID relatedMaidId, String label, long day) {
        String prompt = PromptTexts.broadcastPrompt(label);
        // v1.5.65:解析关系女仆(女儿)的妈妈 UUID——妈妈(妻子)是孩子的亲生母亲,
        // 不需要"得知"自己的孩子,更不该说"这就是主人的女儿吗"(那是外人的话)
        UUID relatedMotherId = null;
        for (EntityMaid m : player.m_9236_().m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0))) {
            if (m.m_20148_().equals(relatedMaidId)) {
                relatedMotherId = RelationshipExemption.readMotherUuid(m);
                break;
            }
        }
        for (EntityMaid maid : player.m_9236_().m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0))) {
            if (maid.m_20148_().equals(relatedMaidId)) {
                continue; // 不广播给关系女仆本人
            }
            if (relatedMotherId != null && relatedMotherId.equals(maid.m_20148_())) {
                continue; // v1.5.65:不广播给孩子的妈妈(妻子)
            }
            if (!maid.m_21824_() || !maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                continue;
            }
            if (this.lastBroadcastDay.getOrDefault(maid.m_20148_(), -1L) == day) {
                continue; // 今天已广播过
            }
            // v1.4.2:无 LLM/配额满 → 固定文本气泡(同样算已广播,防每轮扫描刷屏)
            DialogueDispatcher.chatWithQuota(maid, player, prompt,
                    PromptTexts.broadcastFallback(label, maid.m_7755_().getString()));
            this.lastBroadcastDay.put(maid.m_20148_(), day);
        }
    }
}
