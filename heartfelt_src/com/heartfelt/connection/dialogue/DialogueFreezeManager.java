package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话选择性静止(v1.5.5,原 AdjusterFreezeManager 迁入)——用户需求:
 * Alt+J LLM 对话面板打开期间,其他生物(含其他女仆)停止静止,但玩家与
 * 【正在对话的目标女仆】仍可移动;其他场合(调整器等)不触发时间暂停。
 *
 * 机制:打开对话面板 → 冻结当前维度所有【非玩家、非目标女仆】的 Mob
 * (setNoAi(true) = m_21553_,生物不执行 AI:不移动/不攻击/不寻路)。
 * 目标女仆按 UUID 豁免,其余女仆同样冻结。关闭面板(客户端 onClose →
 * C2S ChatFreezePacket)→ 恢复。
 * 兜底恢复:玩家登出 / 世界保存 / 服务器停止 / 超时(5 分钟无活动)。
 *
 * 补冻:会话期间每 2 秒扫描一次,新生成的生物(刷怪/召唤)自动补冻
 * (同样豁免玩家与目标女仆)。
 *
 * 防残留:实体重新加入世界时,非冻结目标的 noAI 实体一律恢复
 * (崩溃/区块卸载导致的 NoAI 持久化残留自动修复,天然 noAI 实体无副作用)。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class DialogueFreezeManager {
    /** 无活动超时自动恢复(tick,5 分钟) */
    private static final long FREEZE_TIMEOUT = 300 * 20L;
    /** 补冻/超时检查间隔(tick,2 秒) */
    private static final int SCAN_INTERVAL = 40;
    /** 审计 H-1：冻结范围限制在玩家附近，禁止全维冻结 */
    private static final double FREEZE_RADIUS = 32.0;

    /** 冻结会话:player UUID -> 会话(维度 + 目标女仆 + 冻结实体 UUID + 最后活动 tick) */
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 审计 H-11：同一玩家的活跃冻结来源（chat/adjuster/confession），避免关闭一个界面误解冻另一个 */
    private static final Map<UUID, Set<String>> ACTIVE_SOURCES = new ConcurrentHashMap<>();

    private DialogueFreezeManager() {
    }

    private static final class Session {
        final ServerLevel level;
        final Set<UUID> frozen = new HashSet<>();
        /** 目标女仆 UUID(不冻结,可移动);null 表示无目标 */
        UUID targetMaidId;
        long lastActive;

        Session(ServerLevel level, long now) {
            this.level = level;
            this.lastActive = now;
        }
    }

    // ==================== 冻结 / 恢复 ====================

    /**
     * 开始冻结(打开对话面板)。幂等:已有会话只刷新活动时间,不重建
     * (避免每次刷新都闪一下恢复)。只冻结非玩家、非目标女仆的 Mob。
     */
    public static void startFreeze(ServerPlayer player, EntityMaid targetMaid) {
        startFreeze(player, targetMaid, "chat");
    }

    /** 带来源标识的冻结入口（审计 H-11：chat/adjuster/confession 互不误关） */
    public static void startFreeze(ServerPlayer player, EntityMaid targetMaid, String source) {
        if (player == null || !(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        UUID playerId = player.m_20148_();
        UUID targetId = targetMaid == null ? null : targetMaid.m_20148_();
        ACTIVE_SOURCES.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(source);
        Session existing = SESSIONS.get(playerId);
        if (existing != null) {
            existing.lastActive = level.m_46467_();
            // v1.5.114:会话存续期间切换目标女仆(如调整器不关直接对另一女仆打开)
            // ——旧版豁免名单不更新,新目标可能仍被冻着。更新豁免并把新目标解冻。
            if (targetId != null && !targetId.equals(existing.targetMaidId)) {
                if (existing.frozen.remove(targetId)) {
                    Entity newTarget = level.m_8791_(targetId);
                    if (newTarget instanceof Mob mob) {
                        mob.m_21553_(false);
                    }
                }
                existing.targetMaidId = targetId;
            }
            return;
        }
        Session session = new Session(level, level.m_46467_());
        session.targetMaidId = targetId;
        net.minecraft.world.phys.AABB area = player.m_20191_().m_82400_(FREEZE_RADIUS);
        for (Mob mob : level.m_45976_(Mob.class, area)) {
            if (mob instanceof Player) {
                continue; // 玩家可移动
            }
            if (targetId != null && mob.m_20148_().equals(targetId)) {
                continue; // 目标女仆(正在对话的那个)可移动
            }
            mob.m_21553_(true); // setNoAi:生物静止(其他女仆同样冻结)
            session.frozen.add(mob.m_20148_());
        }
        SESSIONS.put(playerId, session);
    }

    /** 恢复冻结(关闭对话面板 / 兜底) */
    public static void stopFreeze(ServerPlayer player) {
        if (player != null) {
            stopFreeze(player.m_20148_());
        }
    }

    /** 关闭指定来源（审计 H-11）：仅当该玩家没有其他活跃冻结来源时才真正解冻 */
    public static void stopFreeze(ServerPlayer player, String source) {
        if (player == null) {
            return;
        }
        UUID playerId = player.m_20148_();
        Set<String> sources = ACTIVE_SOURCES.get(playerId);
        if (sources != null) {
            sources.remove(source);
            if (sources.isEmpty()) {
                ACTIVE_SOURCES.remove(playerId);
                stopFreeze(playerId);
            }
        }
    }

    public static void stopFreeze(UUID playerId) {
        ACTIVE_SOURCES.remove(playerId);
        Session session = SESSIONS.remove(playerId);
        if (session == null) {
            return;
        }
        for (UUID id : session.frozen) {
            Entity e = session.level.m_8791_(id);
            if (e instanceof Mob mob) {
                mob.m_21553_(false); // 恢复 AI
            }
        }
    }

    // ==================== 周期维护 ====================

    /** 每 2 秒:超时会话自动恢复 + 新生物补冻 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long tick = server.m_129921_();
        if (tick % SCAN_INTERVAL != 0) {
            return;
        }
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (tick - session.lastActive > FREEZE_TIMEOUT) {
                stopFreeze(entry.getKey()); // 无活动超时:自动恢复
                continue;
            }
            // 补冻:会话期间新出现的 Mob(同样跳过玩家与目标女仆)
            ServerPlayer sp = session.level.m_8791_(entry.getKey()) instanceof ServerPlayer ?
                    (ServerPlayer) session.level.m_8791_(entry.getKey()) : null;
            if (sp == null) {
                continue;
            }
            net.minecraft.world.phys.AABB area = sp.m_20191_().m_82400_(FREEZE_RADIUS);
            for (Mob mob : session.level.m_45976_(Mob.class, area)) {
                if (mob instanceof Player) {
                    continue;
                }
                if (session.targetMaidId != null && mob.m_20148_().equals(session.targetMaidId)) {
                    continue;
                }
                if (!session.frozen.contains(mob.m_20148_())) {
                    mob.m_21553_(true);
                    session.frozen.add(mob.m_20148_());
                }
            }
        }
    }

    /** 世界保存:恢复全部(防 NoAI 持久化残留) */
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (UUID playerId : new java.util.ArrayList<>(SESSIONS.keySet())) {
            stopFreeze(playerId);
        }
    }

    /** 玩家登出:恢复该玩家会话 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopFreeze(player.m_20148_());
        }
    }

    /** 服务器停止:恢复全部 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID playerId : new java.util.ArrayList<>(SESSIONS.keySet())) {
            stopFreeze(playerId);
        }
    }

    /**
     * 实体加入世界时兜底:非冻结目标的 noAI 实体一律恢复——
     * 崩溃/区块卸载导致 NoAI 持久化残留时,重载后自动复原,不会永久变傻。
     * (天然 noAI 的实体恢复后也不会有 AI 逻辑,行为不变,无副作用)
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_() || SESSIONS.isEmpty()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        UUID id = mob.m_20148_();
        boolean frozenTarget = false;
        for (Session s : SESSIONS.values()) {
            if (s.level == event.getLevel() && s.frozen.contains(id)) {
                frozenTarget = true;
                break;
            }
        }
        if (!frozenTarget) {
            mob.m_21553_(false); // 非冻结目标:恢复 AI(防残留)
        }
    }
}
