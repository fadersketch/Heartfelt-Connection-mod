package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动说话仲裁器(v1.5.96,借鉴 MaidSoulCore SpeechArbiter 思路)——
 * 解决"多个关系女仆同时抢着说话/冒气泡"。
 *
 * 设计:heartfelt 的"说话"全是【对主人说】,所以仲裁粒度 = 每个玩家同时
 * 只有一只女仆在"主动开口"。按 owner UUID 分区,互不干扰(多人各自独立)。
 *
 * 通道(从低到高):
 * - AMBIENT:日常主动(思慕气泡等)——最低,基本只在自己独占时能说;
 * - INTIMATE:关系互动(女儿任务汇报等)——普通主动说话;
 * - PLAYER_REPLY:回应玩家动作(抱起/被打/哀悼/特殊奶)——最高,可打断前者。
 *
 * 判定:
 * - 该玩家当前无人在说 / 窗口已过 → SPEAK(接管);
 * - 就是同一只女仆 → SPEAK(续期);
 * - 更高通道且优先级更高 → INTERRUPT(接管,旧说话者让位);
 * - 否则 → WAIT(让位,这次不说,等下一轮扫描)。
 *
 * 优先级(同通道争抢时用):距离 0.3 + 关系 0.4 + 相识 0.3。
 * 关系越亲(妻子/恋人/女儿)、离主人越近、认识越久,越优先开口。
 *
 * 使用范围(v1.5.96 第一期):只挂在【周期性主动气泡】上——思慕气泡(AMBIENT)
 * 与女儿任务汇报(INTIMATE)。它们每 30 秒/任务结束就会触发,多女仆时最容易
 * 同时冒泡;被让位时本次不发,下一轮冷却自然重试,不丢话。
 * 广播/家庭互动等【一次性】互动不上仲裁——它们本就每日去重、按场景错开,
 * 强行排他反而会吞掉"所有女仆一起庆祝"的设计(让位后当日标记已置,不再重试)。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class DialogueArbiter {
    /** 主动说话窗口(tick,4 秒)——窗口内同玩家只让一只女仆"主动"开口 */
    private static final int WINDOW_TICKS = 80;
    /** 玩家回应通道窗口(tick,2.5 秒)——被打/被抱起等即时回应稍短 */
    private static final int REPLY_WINDOW_TICKS = 50;

    public enum Channel {
        AMBIENT, INTIMATE, PLAYER_REPLY
    }

    public enum Verdict {
        SPEAK, WAIT, INTERRUPT
    }

    /** 每个玩家的说话槽(该玩家当前"谁在开口") */
    private static final class Slot {
        final UUID speakerId;
        final Channel channel;
        final int priority;
        long untilTick;

        Slot(UUID speakerId, Channel channel, int priority, long untilTick) {
            this.speakerId = speakerId;
            this.channel = channel;
            this.priority = priority;
            this.untilTick = untilTick;
        }
    }

    private static final Map<UUID, Slot> SLOTS = new ConcurrentHashMap<>();

    private DialogueArbiter() {
    }

    /** 每 2 秒清理过期槽位(防 Map 无限增长;随服务端 tick 驱动) */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.m_129921_() % 40 != 0) {
            return;
        }
        sweep(server.m_129921_());
    }

    /**
     * 请求开口。返回 SPEAK/INTERRUPT 表示"可以说"(INTERRUPT 同时接管槽位),
     * WAIT 表示"让位,这次不说"。nowTick 用游戏刻(level.gameTime)。
     */
    public static synchronized Verdict request(EntityMaid maid, UUID ownerId,
            Channel channel, long nowTick) {
        if (maid == null || ownerId == null) {
            return Verdict.WAIT;
        }
        int duration = channel == Channel.PLAYER_REPLY ? REPLY_WINDOW_TICKS : WINDOW_TICKS;
        UUID maidId = maid.m_20148_();
        int priority = priorityOf(maid);
        Slot slot = SLOTS.get(ownerId);
        if (slot == null || nowTick >= slot.untilTick) {
            SLOTS.put(ownerId, new Slot(maidId, channel, priority, nowTick + duration));
            return Verdict.SPEAK;
        }
        if (slot.speakerId.equals(maidId)) {
            slot.untilTick = nowTick + duration; // 同一女仆续期
            return Verdict.SPEAK;
        }
        if (channel.ordinal() > slot.channel.ordinal() && priority > slot.priority) {
            SLOTS.put(ownerId, new Slot(maidId, channel, priority, nowTick + duration));
            return Verdict.INTERRUPT;
        }
        return Verdict.WAIT;
    }

    /** 便捷封装:能开口则返回 true(SPEAK/INTERRUPT 都算) */
    public static boolean trySpeak(EntityMaid maid, UUID ownerId, Channel channel) {
        long now = maid.m_9236_().m_46467_();
        Verdict v = request(maid, ownerId, channel, now);
        return v == Verdict.SPEAK || v == Verdict.INTERRUPT;
    }

    /** 窗口过期清理(随扫描调用,防 Map 无限增长) */
    public static void sweep(long nowTick) {
        if (SLOTS.isEmpty()) {
            return;
        }
        SLOTS.entrySet().removeIf(e -> nowTick >= e.getValue().untilTick);
    }

    // ==================== 优先级 ====================

    /** 说话优先级 0~100:距离 0.3 + 关系 0.4 + 相识 0.3 */
    private static int priorityOf(EntityMaid maid) {
        // 关系 0.4:妻子 100 / 恋人 85 / 女儿 90 / 深爱暗恋 70 / 普通 45
        int rel;
        if (RelationshipExemption.isMarried(maid)) {
            rel = 100;
        } else if (RelationshipExemption.isChild(maid)) {
            rel = 90;
        } else if (RelationshipExemption.isConfessed(maid)) {
            rel = 85;
        } else if (RelationshipExemption.isPartner(maid)) {
            rel = 70; // 好感≥192 的深爱暗恋层
        } else {
            rel = 45;
        }
        // 相识 0.3:按 EVENT_FIRST_MEET 距今游戏日(最多 100)
        long firstMeet = maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET);
        int known = 0;
        if (firstMeet > 0L) {
            long days = Math.max(0L, (maid.m_9236_().m_46467_() - firstMeet) / 24000L);
            known = (int) Math.min(100L, days * 10L);
        }
        return rel * 4 / 10 + known * 3 / 10 + 45 * 3 / 10;
    }
}
