package com.heartfelt.connection.compat;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 孩子监护修正(v1.4.0,Bug1)——魂符收掉妈妈后女儿不再"落地自由"。
 *
 * 原版 bug:女儿出生时被妈妈抱着(INFANT 骑乘)。玩家用魂符把妈妈收走时,
 * maidmarriage 只把孩子放地上(SoulSlabChildBridge.onToItem →
 * MaidCarryChildManager.releaseBeforeMaidTransform),之后女儿变成"普通女仆":
 * 能走、能配置、能用孩子专属任务(附魔/酿药),但 maidmarriage 对话脚本仍按
 * isChild 显示"女儿太小不能走路"——行为与文本完全脱节。
 *
 * 修复(用户选择方案:女儿强制坐下不能动):
 * - 妈妈被收走(ToItem)时:女儿(乘客或同 owner 的孩子)强制坐下
 *   (m_21837_=setOrderedToSit,坐下后 TLM 不移动、不工作),写
 *   heartfelt_waiting_mother 标记 + 系统消息;
 * - 妈妈回来(ToMaid)时:清除标记、解除坐下——INFANT 阶段由原版
 *   tickInfantCarryState 自动重新抱起。
 *
 * 事件类在 TLM API(original_tlm.jar 在 classpath),直接强类型监听。
 */
public final class ChildGuardManager {

    @SubscribeEvent
    public void onMaidToItem(MaidAndItemTransformEvent.ToItem event) {
        EntityMaid mother = event.getMaid();
        if (mother == null || mother.m_9236_().f_46443_) {
            return;
        }
        UUID motherUuid = mother.m_20148_();
        // v1.5.96:实体即将转为物品(收魂符)——清理该女仆的瞬态运行时状态。
        // 放出来时实体 UUID 会变,内存 Map 里旧 UUID 条目既无法命中新实体(原值类
        // 状态已改落 NBT),又会残留泄漏/占用告白前摇会话。跨维度/卸载重载同理。
        com.heartfelt.connection.dialogue.ConfessionApproachManager.purgeMaid(motherUuid);
        com.heartfelt.connection.dialogue.PickupResponseManager.purgeMaid(motherUuid);
        final Player owner = mother.m_269323_() instanceof Player p ? p : null;
        // 找女儿:优先骑乘在妈妈身上的孩子;否则同维度、同 owner、mother==我 的孩子
        for (Entity passenger : mother.m_20197_()) {
            if (passenger instanceof EntityMaid child) {
                guardChild(child, motherUuid, owner);
            }
        }
        // 兜底:同维度扫描(孩子可能已落地——maidmarriage 的 release 顺序不确定)
        if (mother.m_9236_() instanceof ServerLevel serverLevel && owner != null) {
            for (EntityMaid child : serverLevel.m_6443_(EntityMaid.class,
                    mother.m_20191_().m_82400_(48.0),
                    e -> e.m_6084_() && e.m_21824_()
                            && com.heartfelt.connection.dialogue.DialogueDispatcher.isOwner(e, owner)
                            && isChildOf(e, motherUuid))) {
                guardChild(child, motherUuid, owner);
            }
        }
    }

    @SubscribeEvent
    public void onMaidToMaid(MaidAndItemTransformEvent.ToMaid event) {
        EntityMaid mother = event.getMaid();
        if (mother == null || mother.m_9236_().f_46443_) {
            return;
        }
        UUID motherUuid = mother.m_20148_();
        if (mother.m_9236_() instanceof ServerLevel serverLevel) {
            for (EntityMaid child : serverLevel.m_6443_(EntityMaid.class,
                    mother.m_20191_().m_82400_(64.0),
                    e -> e.m_6084_() && isChildOf(e, motherUuid)
                            && e.getPersistentData().m_128471_(HeartfeltTags.WAITING_MOTHER))) {
                // 妈妈回来了:解除等待,解除坐下(INFANT 由原版自动重新抱起)
                child.getPersistentData().m_128473_(HeartfeltTags.WAITING_MOTHER);
                child.m_21837_(false);
                Player owner = child.m_269323_() instanceof Player p ? p : null;
                if (owner != null) {
                    owner.m_213846_(Component.m_237113_(
                            "\u00a7d" + child.m_7755_().getString()
                                    + "\u00a7r （看到妈妈回来了，一下子精神起来，朝妈妈伸出小手。）"));
                }
            }
        }
    }

    /** 女儿强制坐下 + 等待标记 + 系统消息(幂等:已标记则只维持坐下) */
    private static void guardChild(EntityMaid child, UUID motherUuid, Player owner) {
        if (child == null || !child.m_6084_()) {
            return;
        }
        boolean first = !child.getPersistentData().m_128471_(HeartfeltTags.WAITING_MOTHER);
        child.getPersistentData().m_128379_(HeartfeltTags.WAITING_MOTHER, true);
        // 强制坐下(坐下后 TLM 不移动、MaidWorkManager 判定 SITTING 不工作)
        child.m_21837_(true);
        // 若还骑乘在妈妈身上,先放下来(妈妈已不在世界,骑乘会悬空)
        if (child.m_20159_()) {
            child.m_8127_();
        }
        if (first && owner != null) {
            owner.m_213846_(Component.m_237113_(
                    "\u00a7c" + child.m_7755_().getString()
                            + "\u00a7r （见妈妈被收了起来，乖乖地坐在地上，一动也不动地等着妈妈回来……）"));
        }
    }

    /** 该女仆是否是 motherUuid 的孩子(persistent 标记或 TaskData 血缘) */
    private static boolean isChildOf(EntityMaid maid, UUID motherUuid) {
        if (maid.getPersistentData().m_128403_("maidmarriage_mother_uuid")
                && motherUuid.equals(maid.getPersistentData().m_128342_("maidmarriage_mother_uuid"))) {
            return true;
        }
        return com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)
                && motherUuid.equals(com.heartfelt.connection.relationship.RelationshipExemption.readMotherUuid(maid));
    }

    // ==================== v1.5.21:幼年女儿约束(强制坐姿 + 任务锁空闲) ====================

    /** 空闲任务缓存(TLM TaskManager.getIdleTask,首次获取后缓存) */
    private static com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask IDLE_TASK = null;

    /** v1.5.26:任务拉回系统提示冷却(tick,2 秒)——maid UUID -> 上次提示时刻 */
    private static final java.util.Map<UUID, Long> TASK_PULL_MSG_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TASK_PULL_MSG_COOLDOWN = 40L;
    /** v1.5.48:幼女武器拿不住提示冷却——maid UUID -> 上次提示时刻 */
    private static final java.util.Map<UUID, Long> HAND_STRIP_MSG_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long HAND_STRIP_MSG_COOLDOWN = 40L;
    /** v1.5.48:女儿背叛清除检查周期(tick,2 秒)——防女儿打死爸爸 */
    private long betraySweepTick = 0;

    /** v1.5.357:每女仆上一 tick 的成长阶段(运行时)——检测 INFANT→JUVENILE 一次性站起
     *  (不依赖可能陈旧的 CHILD_STAGE 标签;幼儿阶段不再持续强制站立,自由坐/站) */
    private final java.util.Map<UUID, MaidMarriageCompat.ChildStage> lastStage =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 是否"太小"(INFANT/JUVENILE 女儿)——不播语音、任务锁空闲、手持限食物 */
    public static boolean isTooSmall(EntityMaid maid) {
        if (maid == null || !com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            return false;
        }
        MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
        return stage == MaidMarriageCompat.ChildStage.INFANT
                || stage == MaidMarriageCompat.ChildStage.JUVENILE;
    }

    /** v1.5.64:是否"婴儿"(仅 INFANT)——唯一不能行走的阶段(强制坐下);
     *  幼儿(JUVENILE)可以行走,但与婴儿一样禁语音/锁任务/手持限食物(isTooSmall) */
    public static boolean isInfant(EntityMaid maid) {
        if (maid == null || !com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            return false;
        }
        return MaidMarriageCompat.childStage(maid) == MaidMarriageCompat.ChildStage.INFANT;
    }

    /**
     * v1.5.21:对幼年女儿强制坐姿 + 任务锁空闲(自愈式,无 mixin 拦截):
     * - 没被妈妈抱着(未骑乘)时强制坐下——玩家解除坐姿会被坐回去;
     * - 任务若非空闲(挖矿/建造/附魔等)拉回空闲——"直到慢慢长大之后才能帮忙做任务";
     * 被妈妈抱着时维持原版抱起行为(跳过坐姿强制)。
     *
     * v1.5.26(用户要求"小婴儿"强化):
     * - 周期 40 tick → 5 tick(0.25 秒)——任务/坐姿"瞬间拉回";
     * - 任务被拉回空闲时给主人系统提示"年龄太小"(2 秒冷却防刷屏);
     * - 手持物品限制:主手/副手只能拿食物,武器/工具/杂物放回背包(背包满丢脚下)。
     */
    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        // v1.5.72:婴儿强制坐下【每 tick】——旧 5 tick(0.25 秒)间隔内,被背叛女仆
        // 攻击时战斗/自保 AI 会把婴儿短暂拉起站起反击("短暂的站起和攻击");
        // 每 tick 按回坐下,站起窗口缩到 1 tick 内不可感知,婴儿永远坐姿(无法攻击)。
        // 任务锁/手持限食物与坐姿无关,维持 5 tick 节流即可(见下方)。
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                if (!isTooSmall(maid) || !isInfant(maid)) {
                    continue;
                }
                // v1.5.81:心契誓约儿童交互中(ChildInteractionManager 会话)——交互
                // lockMaid 会锁定站立+位置,每 tick 强坐会让 isValidInteractionPair
                // 判坐姿失效 → 会话立刻终止 → Alt+J 面板"进入一下就瞬间退出"。
                // 交互中跳过本轮强坐,交互结束后自然恢复。
                if (inChildInteraction(maid)) {
                    continue;
                }
                // 未骑乘(没被妈妈抱着)时强制坐,被妈妈抱着维持原版
                if (!maid.m_20159_()) {
                    maid.m_21837_(true);
                }
            }
        }
        // v1.5.357:婴儿→幼儿"站起一次"(可靠触发)。用户指出每 tick 强制站立是本末倒置——
        // 正确语义:强制坐下只约束婴儿(上方 isInfant 判定已天然解除幼儿的强坐),
        // 升级瞬间【站起一次】,之后幼儿自由坐/站。用运行时 Map 记录上一 tick 阶段,
        // 检测到 INFANT→JUVENILE 变化即站起——不依赖 CHILD_STAGE 标签(可能陈旧导致
        // checkChildGrowth 跳过),也不持续强制站立。
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                UUID id = maid.m_20148_();
                if (!isTooSmall(maid)) {
                    this.lastStage.remove(id);
                    continue;
                }
                MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
                if (stage == null) {
                    continue;
                }
                MaidMarriageCompat.ChildStage prev = this.lastStage.put(id, stage);
                if (prev == MaidMarriageCompat.ChildStage.INFANT
                        && stage == MaidMarriageCompat.ChildStage.JUVENILE) {
                    // 解除强制坐下已生效(幼儿不在强坐范围);这里真正站起一次
                    if (maid.m_20159_()) {
                        maid.m_8127_(); // 若还被妈妈抱着(骑乘)先放下
                    }
                    maid.m_21837_(false);
                    maid.m_20124_(net.minecraft.world.entity.Pose.STANDING);
                    // v1.5.358:"站起来"系统提示只在【真实 INFANT→JUVENILE 升级瞬间】
                    // 发一次(每 tick 对比实际阶段的可靠检测;旧版挂 CHILD_STAGE 标签判定,
                    // 阶段数据震荡/标签陈旧时会在错误时机误发——用户:"摇摇晃晃的站起来
                    // 随机触发,不符合情境")。消息+旁白气泡与站起动作同帧,情境一致。
                    try {
                        if (com.heartfelt.connection.config.HeartfeltConfig.GROWTH_EVENT_ENABLED.get()) {
                            player.m_213846_(net.minecraft.network.chat.Component.m_237113_(
                                    com.heartfelt.connection.prompt.PromptTexts.growthMessage(
                                            maid.m_7755_().getString(), stage)));
                            maid.getChatBubbleManager().addTextChatBubble(
                                    com.heartfelt.connection.prompt.PromptTexts.growthFallbackToddler(
                                            maid.m_7755_().getString()));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        // v1.5.77:语音包静音(每 1 秒)——幼儿/婴儿/伤心窗口女仆【声音频率=0】:
        // TLM 语音包走 MaidSoundInstance → MaidSoundFreqEvent 按 getSoundFreq() 概率
        // 播放,频率 0 = 永不播放(直接静音,不需要额外字段;getAmbientSound mixin
        // 拦不住语音包这条独立路径,用户:"没有必要加入一个额外的字段,直接把声音
        // 频率改为 0 就可以了")
        if (server.m_129921_() % 20 == 0) {
            for (ServerPlayer player : server.m_6846_().m_11314_()) {
                for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                    enforceSoundFreq(maid);
                    enforcePickupXpOnly(maid);
                }
            }
        }
        if (server.m_129921_() % 5 != 0) {
            return;
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                if (!isTooSmall(maid)) {
                    continue;
                }
                // 1. 任务锁空闲 + 拉回时系统提示"年龄太小"
                try {
                    if (IDLE_TASK == null) {
                        IDLE_TASK = com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask();
                    }
                    com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask cur = maid.getTask();
                    if (cur != null && cur != IDLE_TASK
                            && !cur.getUid().equals(IDLE_TASK.getUid())) {
                        maid.setTask(IDLE_TASK);
                        UUID id = maid.m_20148_();
                        Long last = TASK_PULL_MSG_AT.get(id);
                        if (last == null || server.m_129921_() - last >= TASK_PULL_MSG_COOLDOWN) {
                            TASK_PULL_MSG_AT.put(id, (long) server.m_129921_());
                            if (maid.m_269323_() instanceof ServerPlayer owner) {
                                owner.m_213846_(Component.m_237113_(
                                        "\u00a7c" + maid.m_7755_().getString()
                                                + "\u00a7r 年龄太小了,还不能做任务。"));
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                // 2. v1.5.26:手持物品限制——小婴儿只能拿食物
                enforceHandItems(maid);
            }
        }
        // v1.5.48:女儿永不背叛——每 2 秒清除女儿的背叛状态(MC 战斗逻辑下
        // 背叛女儿会攻击并可能打死爸爸;攻击拦截是硬保障,此处从源头清除)
        if (++this.betraySweepTick % 8 == 0) {
            for (ServerPlayer player : server.m_6846_().m_11314_()) {
                for (EntityMaid maid : com.heartfelt.connection.dialogue.DialogueDispatcher.maidsOf(player, 48)) {
                    if (!com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
                        continue;
                    }
                    if (com.heartfelt.connection.compat.CallResponseCompat.isBetraying(maid)) {
                        clearDaughterBetrayal(maid);
                    }
                }
            }
        }
    }

    /** v1.5.48:清除女儿背叛(照调整器 clearBetrayal:复位 + 重新认主 + 站起 + 清标记) */
    private static void clearDaughterBetrayal(EntityMaid maid) {
        try {
            com.heartfelt.connection.compat.CallResponseCompat.resetBetrayal(maid);
            if (maid.m_269323_() instanceof Player owner) {
                maid.m_21816_(owner.m_20148_());
                maid.m_7105_(true);
            }
            maid.m_21837_(false);
            maid.getPersistentData().m_128473_(com.heartfelt.connection.tags.HeartfeltTags.HATED_PLAYER);
            maid.getPersistentData().m_128473_(com.heartfelt.connection.tags.HeartfeltTags.HATED_AT);
            maid.getPersistentData().m_128473_(com.heartfelt.connection.tags.HeartfeltTags.REDEMPTION_PROGRESS);
            maid.getPersistentData().m_128473_(com.heartfelt.connection.tags.HeartfeltTags.REDEMPTION_BETRAYED_AT);
            maid.getPersistentData().m_128473_(com.heartfelt.connection.tags.HeartfeltTags.REDEMPTION_REDEMPTOR);
        } catch (Exception ignored) {
        }
    }

    /** v1.5.82:幼儿/婴儿【拾取类型】强制 = 仅经验(用户方案)——TLM PickType.ONLY_XP
     *  canPickItem=false(反编译实证 EntityMaid 拾取逻辑先查 canPickItem 再捡 ItemEntity)
     *  → 女仆从源头不拾取任何物品 → stripHand 丢下的武器/杂物无需再设无限拾取延迟
     *  (该延迟把主人也卡住),主人碰触即可正常拾起。
     *  每 1 秒强制(玩家在 GUI 改回也会被拉回);长大(CHILD 起)后还原静音前类型。
     *  v1.5.96:原值【落 NBT】(heartfelt_pickup_orig)而非内存 Map——收魂符/跨维度/
     *  卸载重载后实体 UUID 会变,内存 Map 找不到旧条目 → 新实体长大恢复不了原类型
     *  (永久 ONLY_XP)。写 NBT 后魂符带实体走,放出来原值还在。 */
    private static void enforcePickupXpOnly(EntityMaid maid) {
        try {
            if (isTooSmall(maid)) {
                // 首次强制时记录原类型(玩家在 GUI 手动设的类型),之后不再覆盖
                if (!maid.getPersistentData().m_128425_(HeartfeltTags.PICKUP_ORIG, 8)) {
                    maid.getPersistentData().m_128359_(HeartfeltTags.PICKUP_ORIG,
                            maid.getConfigManager().getPickupType().name());
                }
                if (maid.getConfigManager().getPickupType()
                        != com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_XP) {
                    maid.getConfigManager().setPickupType(
                            com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.ONLY_XP);
                }
            } else {
                // 长大:还原原类型并清除 NBT 键(原类型丢失则保持 ONLY_XP,无害)
                if (maid.getPersistentData().m_128425_(HeartfeltTags.PICKUP_ORIG, 8)) {
                    try {
                        maid.getConfigManager().setPickupType(
                                com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.valueOf(
                                        maid.getPersistentData().m_128461_(HeartfeltTags.PICKUP_ORIG)));
                    } catch (Exception ignored) {
                    }
                    maid.getPersistentData().m_128473_(HeartfeltTags.PICKUP_ORIG);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** v1.5.81:是否处于心契誓约儿童交互中(ChildInteractionManager.getInteractionPlayer,全反射软联动) */
    private static boolean inChildInteraction(EntityMaid maid) {
        try {
            Class<?> c = Class.forName("com.example.maidmarriage.compat.ChildInteractionManager");
            java.lang.reflect.Method m = c.getDeclaredMethod("getInteractionPlayer",
                    com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class);
            return m.invoke(null, maid) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** v1.5.80:被静音女仆的【静音前声音频率】运行时记录——条件解除时还原原值,
     *  不再写死 1.0(玩家在女仆配置 GUI 手动设的频率会被覆盖)。
     *  v1.5.96:原值【落 NBT】(heartfelt_sound_orig)——同 PICKUP_ORIG,收魂符/跨维度
     *  后实体重建,UUID 变,内存 Map 恢复不了原频率(永久静音)。写 NBT 跨实体保留。 */
    private static void enforceSoundFreq(EntityMaid maid) {
        try {
            boolean silence = isTooSmall(maid)
                    || com.heartfelt.connection.dialogue.FamilyInteractionManager.isHurtFeeling(maid);
            if (silence) {
                float freq = maid.getConfigManager().getSoundFreq();
                if (freq > 0.0f) {
                    maid.getConfigManager().setSoundFreq(0.0f);
                }
                // 只记一次原值(玩家本就没开声音则记 0,解除后保持 0)
                if (!maid.getPersistentData().m_128425_(HeartfeltTags.SOUND_ORIG, 5)) {
                    maid.getPersistentData().m_128350_(HeartfeltTags.SOUND_ORIG, freq);
                }
            } else {
                // 条件解除:还原原频率并清除 NBT 键
                if (maid.getPersistentData().m_128425_(HeartfeltTags.SOUND_ORIG, 5)) {
                    maid.getConfigManager().setSoundFreq(
                            maid.getPersistentData().m_128457_(HeartfeltTags.SOUND_ORIG));
                    maid.getPersistentData().m_128473_(HeartfeltTags.SOUND_ORIG);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** v1.5.26:幼年女儿手持限食物——主手/副手非食物(武器/工具/杂物/方块一律)直接丢到地上
     *  v1.5.74【修复主副手交叉 bug】:旧版 stripHand(MAIN_HAND, maid.m_21206_()) 检查的
     *  是【副手】物品却清【主手】、OFF_HAND 同理反着来——主手的非食物物品永远不会被
     *  移除(它检查副手),"非武器物品还留在手上"(用户:"不只是武器,只要是非食物的物品
     *  都会这个样子,认标签food")。改为一一对应:主手查主手、副手查副手。 */
    private static void enforceHandItems(EntityMaid maid) {
        stripHand(maid, net.minecraft.world.InteractionHand.MAIN_HAND, maid.m_21205_());
        stripHand(maid, net.minecraft.world.InteractionHand.OFF_HAND, maid.m_21206_());
    }

    /**
     * 单个手持位:非食物 → 直接丢到女仆脚下并清空手持。
     * v1.5.49(用户要求):不再收进背包——"一旦往手里塞武器,武器直接掉在地上",
     * 同时给主人系统提示「XX 还太小,拿不住这个……(掉在了地上)」(2 秒冷却防刷屏);
     * 长大(CHILD 起)后 isTooSmall 为 false,本方法不再执行,效果自然解除。
     * v1.5.34 的背包同类合并/放空槽逻辑已废弃(改主意:不回收,直接丢地上)。
     *
     * v1.5.63(修复无限丢循环):反编译实证——原版 spawnAtLocation(m_19983_)只生成
     * 物品实体、不扣减/不清手持;TLM MaidBrain 的捡拾 AI 会把丢出的武器【自己捡回】
     * (pickupItem 尊重拾取延迟 hasPickUpDelay,但旧版没设延迟)——丢→捡→再丢循环,
     * 且每次用同一 ItemStack 引用生成实体,地上武器无限增多。修复:
     * ① 先清空手持(m_21008_),再丢【副本】(m_41777_ 复制,实体与手持彻底脱钩);
     * ② 实体设置【归属玩家】(m_32052_=setOwner);
     * ③ 实体设置【无限拾取延迟】(m_32010_=setPickUpDelay 1000000)——女仆 AI 尊重
     *    延迟永不捡回,循环断开;玩家不捡则 5 分钟后按原版物品规则消失。
     *
     * v1.5.82(用户方案):不再设无限拾取延迟——改为源头杜绝:enforcePickupXpOnly
     * 把幼儿/婴儿的 TLM 拾取类型【持续强制为 PickType.ONLY_XP】(canPickItem=false,
     * 反编译实证 EntityMaid 拾取逻辑先查 canPickItem 再捡 ItemEntity)→ 女仆根本不
     * 会捡任何物品,丢→捡循环从源头断开。无限延迟(1000000)反而把主人也卡住
     * (原版 playerTouch 先查拾取延迟,主人同样捡不起来——v1.5.75 的
     * ItemEntityPlayerPickupMixin 误注册在 client 段,服务端从不生效,一直没修好)。
     * 去掉延迟后:丢下的物品是普通物品,主人碰触即可正常拾起。
     */
    private static void stripHand(EntityMaid maid, net.minecraft.world.InteractionHand hand,
                                  net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return;
        }
        if (stack.m_41720_().m_41473_() != null) {
            return; // 食物放行(小婴儿可以拿吃的)
        }
        // v1.5.63:先清空手持——原版 m_19983_ 不扣减/不清手,顺序不能依赖它
        maid.m_21008_(hand, net.minecraft.world.item.ItemStack.f_41583_);
        // 丢【副本】到脚下(实体与手持脱钩,防同引用反复生成)
        net.minecraft.world.entity.item.ItemEntity e = maid.m_19983_(stack.m_41777_());
        if (e != null) {
            if (maid.m_269323_() instanceof ServerPlayer owner) {
                e.m_32052_(owner.m_20148_()); // owner=玩家
            }
            // v1.5.82:不再设 1000000 拾取延迟——拾取类型已强制 ONLY_XP,女仆捡不了;
            // 延迟会连主人一起卡住(原版 playerTouch 先查延迟)。
        }
        // 丢下时给主人系统提示"拿不住"(2 秒冷却防刷屏)
        UUID id = maid.m_20148_();
        Long last = HAND_STRIP_MSG_AT.get(id);
        if (last == null || maid.m_9236_().m_46467_() - last >= HAND_STRIP_MSG_COOLDOWN) {
            HAND_STRIP_MSG_AT.put(id, maid.m_9236_().m_46467_());
            if (maid.m_269323_() instanceof ServerPlayer owner) {
                owner.m_213846_(Component.m_237113_(
                        "\u00a77" + maid.m_7755_().getString()
                                + "\u00a7r 还太小,拿不住这个……（掉在了地上）"));
            }
        }
    }
}
