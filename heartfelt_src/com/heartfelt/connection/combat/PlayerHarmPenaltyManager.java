package com.heartfelt.connection.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.dialogue.DialogueDispatcher;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家伤害惩罚(v1.4.1)——冻结层女仆(妻子/恋人/女儿)关系不破坏,但有后果。
 *
 * 原则(用户确认):
 * 1. 无 LLM 也必须完整运作——惩罚机制本体(心情/伤心窗口/赌气坐下/系统消息/气泡)
 *    全部是原版机制;LLM 伤心对话只是可选增强,无 LLM/配额满自动降级。
 * 2. 误伤不能累积惩罚——采用"30 秒窗口内对同一女仆累积 N 次伤害才确认故意":
 *    打怪误伤 1-2 下不算;每次伤害刷新窗口,偶尔误伤不会累积。
 *
 * 惩罚内容:
 * - 心情扣减(每日有上限防刷)
 * - 伤心窗口(heartfelt_hurt_until):窗口内她坐着赌气、不主动互动
 *   (FamilyInteractionManager 检查)、对话注入委屈文本(AftermathPrompt 新档)
 * - 系统消息 + 女仆气泡(固定文本,无 LLM 也可见)
 * - 可选:LLM 伤心对话(走全局配额)
 */
public final class PlayerHarmPenaltyManager {
    /** 伤害判定窗口(tick,30 秒) */
    private static final long HARM_WINDOW_TICKS = 600L;
    /** maid UUID -> 窗口内累积伤害次数(类为服务器生命周期单例,静态与实例等价;静态便于卸载清理) */
    private static final Map<UUID, Integer> harmHits = new ConcurrentHashMap<>();
    /** maid UUID -> 最近一次伤害的游戏 tick(窗口刷新) */
    private static final Map<UUID, Long> harmLastTick = new ConcurrentHashMap<>();
    /** v1.5.9:玩家攻击记录(识别 callresponse 中性源重放)——maid UUID -> player UUID */
    private static final Map<UUID, UUID> LAST_PLAYER_ATTACK = new ConcurrentHashMap<>();
    /** v1.5.9:记录时刻 —— maid UUID -> gameTick */
    private static final Map<UUID, Long> LAST_ATTACK_TICK = new ConcurrentHashMap<>();
    /** v1.5.32:幼儿被击处理同 tick 去重(hurt 入口 + LivingHurtEvent 双路径会重复)——maid UUID -> 上次处理 tick */
    private static final Map<UUID, Long> LAST_TODDLER_HIT = new ConcurrentHashMap<>();
    private static int tick = 0;

    /** 审计修复(1.5.116)：女仆实体卸载/换维度时清理其伤害记录与攻击还原表（防长会话泄漏） */
    @SubscribeEvent
    public void onEntityLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().m_5776_() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        forgetMaid(maid.m_20148_());
    }

    /** 清理某女仆的全部记录（实体卸载时调用） */
    public static void forgetMaid(UUID maidUuid) {
        harmHits.remove(maidUuid);
        harmLastTick.remove(maidUuid);
        LAST_PLAYER_ATTACK.remove(maidUuid);
        LAST_ATTACK_TICK.remove(maidUuid);
        LAST_TODDLER_HIT.remove(maidUuid);
    }

    /**
     * v1.5.9:记录"玩家攻击女仆"(由 EntityMaidHurtRecordMixin 在 hurt HEAD 调用)。
     * callresponse 会用中性源重放伤害并取消原 hurt,导致 LivingHurtEvent
     * 的伤害源是 generic()——惩罚靠这份记录还原玩家身份。
     */
    public static void recordPlayerAttack(EntityMaid maid, ServerPlayer player, long gameTick) {
        LAST_PLAYER_ATTACK.put(maid.m_20148_(), player.m_20148_());
        LAST_ATTACK_TICK.put(maid.m_20148_(), gameTick);
        // v1.5.32:幼儿女儿被玩家【近战】攻击——在 hurt 入口立即处理,
        // 不依赖 LivingHurtEvent 链路(callresponse 重放等场景事件链可能断)
        onToddlerHit(maid, player);
        // v1.5.98:情感引擎喂脉冲——玩家攻击 → 冲突/受伤债上升
        com.heartfelt.connection.affect.AffectStateManager.onHurtByOwner(maid);
    }

    /**
     * v1.5.32/v1.5.35/v1.5.38:幼儿女儿(INFANT/JUVENILE)被伤害——统一处理点
     * (hurt 入口 + LivingHurtEvent 双路径;同 tick 去重防重复扣减):
     * ① 好感度 -1 只对【主人来源】立即生效(无 30 秒评估——小婴儿分不清是非,
     *    主人打一下就掉好感);非主人/非玩家来源只大哭不扣好感;
     * ② 哇哇大哭:任何来源都触发(无冷却——婴儿被弄疼就哭);
     * ③ 消息目标:玩家攻击发给玩家;非玩家来源(怪物/环境)发给主人,主人不在只发气泡。
     */
    public static void onToddlerHit(EntityMaid maid, ServerPlayer player) {
        if (maid == null
                || !com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
            return;
        }
        long now = maid.m_9236_().m_46467_();
        UUID maidUuid = maid.m_20148_();
        // 同 tick 去重:hurt 入口与 LivingHurtEvent 可能都触发,只处理一次
        Long lastTick = LAST_TODDLER_HIT.get(maidUuid);
        if (lastTick != null && lastTick == now) {
            return;
        }
        LAST_TODDLER_HIT.put(maidUuid, now);
        // ① 好感度 -1:只对主人来源(立即,无评估);陌生人/怪物/环境不扣
        boolean favorDropped = false;
        if (player != null && maid.m_21830_(player)) {
            int favor = maid.getFavorability();
            if (favor > 0) {
                maid.setFavorability(Math.max(0, favor - 1));
                favorDropped = true;
            }
        }
        // ② 哇哇大哭:任何来源都触发(系统消息 + 气泡,无冷却)
        ServerPlayer msgTarget = player != null ? player
                : (maid.m_269323_() instanceof ServerPlayer owner ? owner : null);
        if (msgTarget != null) {
            msgTarget.m_213846_(Component.m_237113_(
                    PromptTexts.babyCryMessage(maid.m_7755_().getString(), favorDropped)));
        }
        maid.getChatBubbleManager().addTextChatBubble(
                PromptTexts.babyCryBubble(maid.m_7755_().getString()));
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().m_9236_().f_46443_) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        // 只对冻结层女仆(妻子/恋人/女儿):她们的关系不破坏,但伤害有后果
        if (!RelationshipExemption.isFrozen(maid)) {
            return;
        }
        // 仅玩家主动攻击(近战/远程的 source.getEntity 都是玩家;爆炸/环境不算)。
        // v1.5.9:callresponse 会用中性源重放主人攻击——查攻击记录还原玩家身份。
        Player player = resolvePlayer(event, maid);
        // v1.5.35/v1.5.38:幼儿女儿——任何来源都哇哇大哭;
        // 好感度 -1 只对【主人来源】立即生效(无 30 秒评估;婴儿分不清是非,主人打一下就掉)。
        // hurt 入口(EntityMaidHurtRecordMixin)已处理近战主人来源;此处兜底远程/重放等,
        // onToddlerHit 内部同 tick 去重
        if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
            onToddlerHit(maid, player instanceof ServerPlayer sp ? sp : null);
            return;
        }
        if (player == null || player.m_9236_().f_46443_) {
            return;
        }
        UUID maidUuid = maid.m_20148_();
        long now = maid.m_9236_().m_46467_();
        // 窗口刷新:距上次伤害超过窗口 → 重新计数
        Long last = this.harmLastTick.get(maidUuid);
        int hits = (last != null && now - last < HARM_WINDOW_TICKS)
                ? this.harmHits.getOrDefault(maidUuid, 0) + 1 : 1;
        this.harmHits.put(maidUuid, hits);
        this.harmLastTick.put(maidUuid, now);
        if (hits < HeartfeltConfig.HARM_TRIGGER_HITS.get()) {
            return;
        }
        // 确认故意:触发惩罚,并清计数(避免同窗口连触发)
        this.harmHits.remove(maidUuid);
        this.harmLastTick.remove(maidUuid);
        this.applyPenalty(maid, player, now);
    }

    /** v1.5.9:解析伤害来源玩家——直接玩家源,或中性源重放(查攻击记录) */
    private static Player resolvePlayer(LivingHurtEvent event, EntityMaid maid) {
        net.minecraft.world.entity.Entity direct = event.getSource().m_7640_();
        if (direct instanceof Player player) {
            return player;
        }
        // v1.5.32:远程(弓/弩)direct=箭,getEntity(造成者)是玩家——补查
        net.minecraft.world.entity.Entity cause = event.getSource().m_7639_();
        if (cause instanceof Player player2) {
            return player2;
        }
        // callresponse 中性源重放(directEntity=null):窗口内查玩家攻击记录
        UUID maidId = maid.m_20148_();
        Long at = LAST_ATTACK_TICK.get(maidId);
        if (at == null || maid.m_9236_().m_46467_() - at > HARM_WINDOW_TICKS) {
            return null;
        }
        UUID playerId = LAST_PLAYER_ATTACK.get(maidId);
        if (playerId == null || !(maid.m_9236_() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }
        for (ServerPlayer sp : level.m_6907_()) {
            if (sp.m_20148_().equals(playerId)) {
                return sp;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        if (++this.tick < 40) {
            return; // 每 2 秒扫一次赌气状态
        }
        this.tick = 0;
        long now = server.m_129921_();
        for (ServerLevel level : server.m_129785_()) {
            for (net.minecraft.world.entity.Entity entity : level.m_8583_()) {
                if (!(entity instanceof EntityMaid maid)) {
                    continue;
                }
                long hurtUntil = maid.getPersistentData().m_128454_(HeartfeltTags.HURT_UNTIL);
                if (hurtUntil > now) {
                    // 伤心窗口:坐着赌气(坐下后不移动不工作)
                    if (!maid.isMaidInSittingPose()) {
                        maid.m_21837_(true);
                    }
                    // v1.5.44:情绪值自然降到沮丧档并【锁定】(maidmarriage MoodState:
                    // mood<10 = DEPRESSED)——每 2 秒钳制,防每日心情自然回升;
                    // 窗口解除后不再钳制,由 maidmarriage 原版机制自然恢复
                    int mood = com.heartfelt.connection.compat.MaidMarriageCompat.moodValue(maid);
                    if (mood > 9) {
                        com.heartfelt.connection.compat.MaidMarriageCompat.addMood(maid, 9 - mood);
                    }
                } else if (hurtUntil > 0L) {
                    // 窗口结束:解除赌气,清标记
                    maid.getPersistentData().m_128473_(HeartfeltTags.HURT_UNTIL);
                    if (maid.isMaidInSittingPose() && !maid.getPersistentData().m_128471_(HeartfeltTags.WAITING_MOTHER)) {
                        maid.m_21837_(false);
                    }
                    // v1.5.45:伤心解除——女仆口吻系统消息(她消气了);
                    // 沮丧锁定随之解除(不再钳制 mood),Alt+J 恢复可互动(isHurtFeeling 已 false)
                    // v1.5.46:女儿(少女/成女)用女儿专属消气文本
                    if (maid.m_269323_() instanceof ServerPlayer owner) {
                        boolean child = com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid);
                        owner.m_213846_(Component.m_237113_(
                                child ? PromptTexts.hurtRecoverMessageChild(maid.m_7755_().getString())
                                        : PromptTexts.hurtRecoverMessage(maid.m_7755_().getString())));
                    }
                }
            }
        }
    }

    /** 窗口内是否在伤心(供家庭互动/对话跳过) */
    public static boolean isHurtFeeling(EntityMaid maid) {
        return maid.getPersistentData().m_128454_(HeartfeltTags.HURT_UNTIL) > maid.m_9236_().m_46467_();
    }

    private static void applyPenalty(EntityMaid maid, Player player, long now) {
        long day = now / 24000L;
        long recordedDay = maid.getPersistentData().m_128454_(HeartfeltTags.HURT_PENALTY_DAY);
        int count = 0;
        if (recordedDay == day) {
            count = maid.getPersistentData().m_128451_(HeartfeltTags.HURT_PENALTY_COUNT);
        } else {
            maid.getPersistentData().m_128356_(HeartfeltTags.HURT_PENALTY_DAY, day);
        }
        int cap = HeartfeltConfig.HARM_DAILY_CAP.get();
        if (cap > 0 && count >= cap) {
            return; // 每日上限,防刷
        }
        maid.getPersistentData().m_128405_(HeartfeltTags.HURT_PENALTY_COUNT, count + 1);
        // 伤心窗口
        long until = now + Math.max(1200L, HeartfeltConfig.HARM_FEELING_TICKS.get());
        maid.getPersistentData().m_128356_(HeartfeltTags.HURT_UNTIL, until);
        // 心情扣减(原版机制,无 LLM 也生效)
        int moodDrop = HeartfeltConfig.HARM_MOOD_DROP.get();
        if (moodDrop > 0) {
            com.heartfelt.connection.compat.MaidMarriageCompat.addMood(maid, -moodDrop);
        }
        // 强制坐下赌气
        maid.m_21837_(true);
        // v1.5.46:女儿(少女/成女)专属伤心文本——机制与妈妈一致,文本单独设计;
        // 幼儿(isTooSmall)已在上游 return,不进伤心
        boolean isChild = com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid);
        // 系统消息(玩家) + 女仆气泡(固定文本,无 LLM 可见)
        if (player instanceof ServerPlayer sp) {
            sp.m_213846_(Component.m_237113_(
                    isChild ? PromptTexts.harmPenaltyMessageChild(maid.m_7755_().getString())
                            : PromptTexts.harmPenaltyMessage(maid.m_7755_().getString())));
        }
        maid.getChatBubbleManager().addTextChatBubble(
                isChild ? PromptTexts.harmPenaltyBubbleChild(maid.m_7755_().getString())
                        : PromptTexts.harmPenaltyBubble(maid.m_7755_().getString(),
                                PromptTexts.termOfAddress(maid)));
        // 可选 LLM 伤心对话(无 LLM / 配额满 → 静默降级,上面固定文本已兜底)
        if (HeartfeltConfig.HARM_LLM_REACTION.get() && player instanceof ServerPlayer sp2) {
            DialogueDispatcher.chatWithQuota(maid, sp2,
                    isChild ? PromptTexts.harmPenaltyLLMPromptChild(maid.m_7755_().getString())
                            : PromptTexts.harmPenaltyLLMPrompt(maid.m_7755_().getString()));
        }
    }
}
