package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.network.HeartfeltNetwork;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告白前摇(v1.5.0)——女仆主动告白的"走到身边再触发"状态机。
 *
 * 用户需求:告白不要突然全屏突脸,要有前摇——女仆先检索周围威胁,没有威胁
 * 则系统消息提示("她似乎有什么想说的"),然后走向玩家,走到身边才触发对话。
 *
 * 流程:
 * 1. MaidConfessionManager.attemptConfessions 判定通过 → startApproach:
 *    - 系统消息(玩家可见,只发一次)
 *    - 预写 CONFESSION_BY="maid"(供补记检测)
 *    - 站起(坐姿会清 WALK_TARGET)
 *    - Brain WALK_TARGET 走向玩家当前位置
 *    - 记录会话
 * 2. onServerTick 每 20 tick:
 *    - 刷新 WALK_TARGET 到玩家当前位置(玩家移动时持续跟随)
 *    - 取消:女仆死亡 / 玩家离线 / 超时 / 期间出现威胁
 *    - 到达(距离 ≤ approachDistance)→ 发 S2C OpenMaidMarriageConfessionPacket
 *      跳转 maidmarriage 告白选项界面 → 移除会话
 * 3. 补记检测(每 40 tick):maidmarriage 界面 accept 后(completeConfession 由
 *    maidmarriage 自己写),heartfelt 补写 CONFESSION_AT/心情,记忆不丢。
 */
@Mod.EventBusSubscriber(modid = "heartfelt_connection")
public final class ConfessionApproachManager {
    /** 前摇会话:maid UUID -> (目标玩家 UUID, 开始 tick) */
    private static final Map<UUID, Session> APPROACHING = new ConcurrentHashMap<>();
    /** 审计 H-9：女仆卸载时暂存原任务 UID，重载后恢复（实体 UUID 未变时可用） */
    private static final Map<UUID, String> PENDING_TASK_RESTORE = new ConcurrentHashMap<>();
    /** WALK_TARGET 刷新间隔(tick,1 秒) */
    private static final int REFRESH_INTERVAL = 20;
    /** 补记检测间隔(tick,2 秒) */
    private static final int BACKFILL_INTERVAL = 40;

    private ConfessionApproachManager() {
    }

    /**
     * v1.5.96:女仆实体重建(收魂符/跨维度/卸载重载)时清理——实体 UUID 会变,
     * 旧 APPROACHING 条目残留会让新实体无法发起新的前摇(且旧会话任务恢复无意义)。
     * 由 ChildGuardManager.onMaidToItem 统一调用。
     */
    public static void purgeMaid(UUID maidId) {
        if (maidId == null) {
            return;
        }
        Session s = APPROACHING.remove(maidId);
        PENDING_TASK_RESTORE.remove(maidId);
        if (s != null) {
            // 实体已不在世界,任务恢复无从谈起——仅清理会话即可
        }
    }

    private record Session(UUID playerId, long startTick,
                           com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask prevTask) {
    }

    // ==================== 启动前摇 ====================

    /** 启动告白前摇:系统消息 → 站起 → 走向玩家 → 记录会话 */
    public static void startApproach(ServerPlayer player, EntityMaid maid) {
        UUID maidId = maid.m_20148_();
        if (APPROACHING.containsKey(maidId)) {
            // v1.5.112:残留会话(异常/取消路径未清)会永久挡住新前摇——用户反复点
            // 「立即触发主动告白」却毫无反应(连系统提示都不出)。改为强制重启:
            // 清掉旧会话再启动,按钮永远可重试。
            endSession(maidId, maid);
        }
        long now = maid.m_9236_().m_46467_();
        // 1. 系统消息(玩家预期,只发一次)
        player.m_213846_(Component.m_237113_(
                PromptTexts.confessionApproachHint(maid.m_7755_().getString())));
        // 2. 预写告白发起方(maidmarriage 界面 accept 后补记用)
        maid.getPersistentData().m_128359_(HeartfeltTags.CONFESSION_BY, "maid");
        // 3. 站起(坐姿会清 WALK_TARGET,走不过去)
        if (maid.isMaidInSittingPose()) {
            maid.m_21837_(false);
        }
        // v1.5.364:切待机任务——工作任务(建造/烹饪/酿造)每 tick 清 WALK_TARGET,且
        // promaid 的站桩锁(isStill = WORK_STILL_TAG + 工作任务)会取消 MoveToTargetSink,
        // 走向被稳定打断(用户:"准备文本弹出后两三秒跑向主人,但不能稳定触发")。
        // 切 idle 后 WALK_TARGET 独占,走向稳定;会话结束(到达/取消)时恢复原任务。
        com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask prevTask = null;
        try {
            com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask cur = maid.getTask();
            com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask idle =
                    com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask();
            if (cur != null && idle != null && !cur.getUid().equals(idle.getUid())) {
                prevTask = cur;
                maid.setTask(idle);
            }
        } catch (Exception ignored) {
        }
        // 4. 走向玩家
        walkTo(maid, player);
        APPROACHING.put(maidId, new Session(player.m_20148_(), now, prevTask));
        if (prevTask != null) {
            try {
                PENDING_TASK_RESTORE.put(maidId, prevTask.getUid().toString());
            } catch (Exception ignored) {
            }
        }
    }

    /** 设置/刷新 WALK_TARGET 到玩家位置(Brain 寻路,速度/容忍 2 格) */
    private static void walkTo(EntityMaid maid, ServerPlayer player) {
        double speed = HeartfeltConfig.CONFESSION_APPROACH_SPEED.get();
        Brain<?> brain = maid.m_6274_();
        if (brain == null) {
            return;
        }
        brain.m_21879_(MemoryModuleType.f_26370_,
                new WalkTarget(new BlockPosTracker(player.m_20183_()), (float) speed, 2));
    }

    // ==================== 会话维护 ====================

    /** 审计：女仆卸载/移除时清理前摇会话与待恢复任务 */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        UUID id = maid.m_20148_();
        APPROACHING.remove(id);
        // 注意：PENDING_TASK_RESTORE 不在这里清除，卸载重载后仍可恢复原任务；
        // 魂符/实体重建由 purgeMaid 清除。
    }

    /** 审计 H-9：女仆重新加入世界时恢复卸载期间暂停的原任务 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || event.getLevel().m_5776_()) {
            return;
        }
        UUID id = maid.m_20148_();
        String uid = PENDING_TASK_RESTORE.remove(id);
        if (uid == null) {
            return;
        }
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(uid);
            java.util.Optional<com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask> task =
                    com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.findTask(rl);
            if (task.isPresent()) {
                maid.setTask(task.get());
            }
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long tick = server.m_129921_();
        if (tick % REFRESH_INTERVAL == 0) {
            tickApproaching(server, tick);
        }
        if (tick % BACKFILL_INTERVAL == 0) {
            backfillConfession(server);
        }
    }

    /** 推进前摇会话:刷新目标/取消/到达触发 */
    private static void tickApproaching(MinecraftServer server, long tick) {
        if (APPROACHING.isEmpty()) {
            return;
        }
        long timeout = HeartfeltConfig.CONFESSION_APPROACH_TIMEOUT.get();
        // v1.5.99:到达判定与 maidmarriage 门槛对齐——maidmarriage 的
        // handleInteractionToggle 要求 m_20280_()<=5.0625(2.25 格平方),超了
        // 就静默拒绝("对话框不弹")。这里 clamp 到 2.0 格(4.0 平方)留 0.25 格
        // 余量:女仆走到 2.0 格内必定满足 maidmarriage 交互距离,不再出现
        // "我们判到达、maidmarriage 判太远"的卡死(2.25~2.5 格区间)。
        double arriveDist = Math.min(HeartfeltConfig.CONFESSION_APPROACH_DISTANCE.get(), 2.0);
        for (Map.Entry<UUID, Session> entry : APPROACHING.entrySet()) {
            UUID maidId = entry.getKey();
            Session session = entry.getValue();
            ServerPlayer player = findPlayer(server, session.playerId());
            EntityMaid maid = findMaid(server, maidId);
            if (player == null || maid == null || !maid.m_6084_()) {
                endSession(maidId, maid); // 玩家离线/女仆死亡:取消并恢复任务
                continue;
            }
            if (tick - session.startTick() > timeout) {
                // v1.5.112:超时取消要给玩家反馈——此前静默取消,用户只看到
                // 一句"已触发"后没下文,无法判断是没走完还是出错了
                player.m_213846_(Component.m_237113_(
                        PromptTexts.confessionApproachTimeout(maid.m_7755_().getString())));
                endSession(maidId, maid); // 超时:取消并恢复任务
                continue;
            }
            if (!isSafe(maid)) {
                player.m_213846_(Component.m_237113_(
                        PromptTexts.confessionApproachInterrupted(maid.m_7755_().getString())));
                endSession(maidId, maid); // 期间出现威胁:取消并恢复任务
                continue;
            }
            // v1.5.17:到达判定用真实距离(Math.sqrt——旧版 m_20280_ 是平方距离,
            // 与线性 2.5 比较实际 1.58 格才算到);且系统消息发出后至少等
            // CONFESSION_APPROACH_MIN_TICKS(默认 50=2.5s)才拉告白选项——
            // 女仆已在身边时旧版第一秒就触发,前摇文本/走向被跳过、太突兀
            if (tick - session.startTick() >= HeartfeltConfig.CONFESSION_APPROACH_MIN_TICKS.get()
                    && Math.sqrt(maid.m_20280_(player)) <= arriveDist) {
                // 走到身边:选择性静止(其他生物定住,主人与告白女仆可动)+ 跳转告白选项
                endSession(maidId, maid); // 恢复任务(交互接管后由互动锁定)
                // v1.5.112:立即停掉女仆移动——endSession 恢复原任务后女仆可能
                // 继续走开,而 maidmarriage 交互距离门槛 2.25 格(5.0625 平方),
                // 走开一点就"静默拒绝、界面不弹"
                maid.m_21573_().m_26573_();
                maid.m_6710_(null);
                // v1.5.99:先停掉玩家可能残留的互动会话——maidmarriage 的
                // handleInteractionToggle 第一行"已有会话 → stopInteraction 返回",
                // 玩家残留会话(之前 Alt+J 没关/与其他女仆互动中)会让告白界面
                // 被当成"开关关闭"而不弹出。hugStop() 反射 forceStopInteraction
                // (公共 API),幂等(无会话时静默)。
                try {
                    java.lang.reflect.Method forceStop = com.heartfelt.connection.compat.MaidMarriageCompat
                            .hugStop();
                    if (forceStop != null) {
                        com.heartfelt.connection.compat.ReflectUtil.invokeStatic(forceStop, player, maid);
                    }
                } catch (Exception ignored) {
                }
                DialogueFreezeManager.startFreeze(player, maid, "confession");
                // v1.5.112:先发 S2C 让客户端记住断点(remember confession_intro),
                // 同一服务端 tick 直调 maidmarriage handleInteractionToggle 开启
                // 互动(替代原客户端 sendHugMaid 往返——canSendToServer/反射/C2S
                // 时序任一处静默失败界面就不弹,是"前摇完成但对话框不出现"的
                // 主要嫌疑)。同一连接包序:OpenPacket 先到(remember)→
                // HugStateSyncPayload 后到(开屏消费断点),告白前文不会丢。
                HeartfeltNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player),
                        new HeartfeltNetwork.OpenMaidMarriageConfessionPacket(maidId));
                // v1.5.114:开启后【验证】会话真的建立(getInteractionPlayer 非 null)
                // ——handleInteractionToggle 被内部拒绝(坐姿/占用等)时不抛异常,
                // ensureInteraction 的返回值覆盖不了。失败则清客户端断点(v1.5.112
                // 先发的 OpenPacket 已 remember,残留会把玩家下次正常对话误跳进
                // 告白剧本——HugStoryResumeState 只有消费没有过期)并提示。
                boolean opened = com.heartfelt.connection.compat.MaidMarriageCompat
                        .ensureInteraction(player, maid);
                if (!opened || !com.heartfelt.connection.compat.MaidMarriageCompat
                        .isMaidInteracting(maid)) {
                    HeartfeltNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player),
                            new HeartfeltNetwork.OpenMaidMarriageConfessionPacket(null));
                    player.m_213846_(Component.m_237113_(
                            PromptTexts.confessionApproachOpenFailed(maid.m_7755_().getString())));
                    // 审计 H-10：互动开启失败必须解除冻结，否则客户端无界面时会被冻满 5 分钟
                    DialogueFreezeManager.stopFreeze(player, "confession");
                }
                continue;
            }
            // 还没到:刷新走向目标(跟随玩家移动)
            walkTo(maid, player);
        }
    }

    /** v1.5.364:结束前摇会话(移除 + 恢复原任务) */
    private static void endSession(UUID maidId, EntityMaid maid) {
        Session s = APPROACHING.remove(maidId);
        if (s == null) {
            return;
        }
        if (maid != null && s.prevTask() != null) {
            try {
                maid.setTask(s.prevTask());
            } catch (Exception ignored) {
            }
            PENDING_TASK_RESTORE.remove(maidId);
        }
    }

    /** 补记检测:maidmarriage 界面 accept 后,heartfelt 补写告白时间戳/心情 */
    private static void backfillConfession(MinecraftServer server) {
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : DialogueDispatcher.maidsOf(player, 48)) {
                if (!RelationshipExemption.isConfessed(maid)) {
                    continue;
                }
                // 只补"女仆主动"路径(前摇已预写 CONFESSION_BY="maid")
                if (!"maid".equals(maid.getPersistentData().m_128461_(HeartfeltTags.CONFESSION_BY))) {
                    continue;
                }
                if (maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT) > 0L) {
                    continue; // 已补记过
                }
                maid.getPersistentData().m_128356_(HeartfeltTags.CONFESSION_AT,
                        maid.m_9236_().m_46467_());
                // v1.5.98:情感引擎喂脉冲——女仆主动告白成功 → 亲密大涨
                com.heartfelt.connection.affect.AffectStateManager.onConfessionSuccess(maid);
                // v1.5.18:确认关系 → 写主人全局标记(绝对压制吃醋)
                com.heartfelt.connection.relationship.RelationshipExemption.markDedicated(player);
                MaidMarriageCompat.addMood(maid, 2); // 喜事心情小涨
                player.m_213846_(Component.m_237113_(
                        PromptTexts.confessionAccepted(maid.m_7755_().getString())));
            }
        }
    }

    // ==================== 工具 ====================

    /** 威胁检查(与 MaidConfessionManager.isSafe 一致):不在战斗,半径内无 Monster */
    private static boolean isSafe(EntityMaid maid) {
        if (maid.m_5448_() != null) {
            return false;
        }
        double radius = HeartfeltConfig.CONFESSION_THREAT_RADIUS.get();
        List<Monster> monsters = maid.m_9236_().m_45976_(
                Monster.class, maid.m_20191_().m_82400_(radius));
        return monsters.isEmpty();
    }

    private static ServerPlayer findPlayer(MinecraftServer server, UUID playerId) {
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player.m_20148_().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    private static EntityMaid findMaid(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.m_129785_()) {
            Entity e = level.m_8791_(maidId);
            if (e instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }
}
