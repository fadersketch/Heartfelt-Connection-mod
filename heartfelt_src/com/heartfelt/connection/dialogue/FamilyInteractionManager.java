package com.heartfelt.connection.dialogue;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.config.HeartfeltConfig;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 家庭互动(v1.1.0;v1.2.2 女儿线拓展)。
 *
 * 三类 LLM 互动(均纳入全局 API 配额,每目标每类每天 1 次):
 * 1. 母女互动(偶数日):女儿在场时,母亲(妻子)或女儿对主人说一句家庭向的话,交替发声。
 * 2. 父女互动(奇数日,v1.2.2):女儿对爸爸(主人)说一句**按成长阶段区分**的话——
 *    幼儿咿呀撒娇 / 少女缠着爸爸 / 成年女儿关心爸爸——与母女文本彻底区隔。
 * 3. 暗恋者感慨:主人已有妻子后,深爱未告白女仆偶尔释然祝福(纯爱)。
 *
 * v1.2.2 成长事件:女儿成长阶段升级时(INFANT→JUVENILE→CHILD→ADULT)触发一次
 * LLM 小剧情("爸爸,我长大了!")+ 系统消息——给成长一个"时刻"。
 */
public class FamilyInteractionManager {
    /** daughter UUID -> 最后母女互动的游戏日 */
    private final Map<UUID, Long> motherDaughterDay = new ConcurrentHashMap<>();
    /** daughter UUID -> 最后父女互动的游戏日 */
    private final Map<UUID, Long> fatherDaughterDay = new ConcurrentHashMap<>();
    /** maid UUID -> 最后暗恋感慨的游戏日 */
    private final Map<UUID, Long> crushDay = new ConcurrentHashMap<>();
    private long lastDay = -1;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long tick = server.m_129921_();
        if (tick % HeartfeltConfig.FAMILY_SCAN_INTERVAL.get() != 0) {
            return;
        }
        long day = tick / 24000L;
        if (day != this.lastDay) {
            this.lastDay = day;
            this.motherDaughterDay.clear();
            this.fatherDaughterDay.clear();
            this.crushDay.clear();
        }
        // v1.5.27:纪念日检查【不依赖 LLM 开关】——纪念日本身有固定文本气泡降级
        // (anniversaryFallback),无 LLM/配额满也应触发(此前在 AIConfig.LLM_ENABLED
        // 门后,LLM 关闭时纪念日从不触发——触发不明确的根因之一)
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            List<EntityMaid> maids = player.m_9236_().m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0));
            this.checkAnniversaries(player, maids, day);
        }
        if (!AIConfig.LLM_ENABLED.get()) {
            return;
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            List<EntityMaid> maids = player.m_9236_().m_45976_(EntityMaid.class, player.m_20191_().m_82400_(48.0));
            // v1.2.2:单双日交替——偶数日母女、奇数日父女
            if (day % 2 == 0) {
                this.motherDaughterInteractions(player, maids, day);
            } else if (HeartfeltConfig.FATHER_DAUGHTER_ENABLED.get()) {
                this.fatherDaughterInteractions(player, maids, day);
            }
            if (HeartfeltConfig.GROWTH_EVENT_ENABLED.get()) {
                this.checkChildGrowth(player, maids);
            }
            this.crushSighs(player, maids, day);
        }
    }

    /** 母女互动:女儿在场时,母亲或女儿对主人说一句家庭向的话(每对每 2 天 1 次) */
    private void motherDaughterInteractions(ServerPlayer player, List<EntityMaid> maids, long day) {
        for (EntityMaid daughter : maids) {
            if (!daughter.m_6084_() || !DialogueDispatcher.isOwner(daughter, player)) {
                continue;
            }
            if (!RelationshipExemption.isChild(daughter) || isHurtFeeling(daughter)) {
                continue;
            }
            // v1.5.31：幼儿女儿（INFANT/JUVENILE）不会说话——母女互动整个跳过
            // （她无法参与"妈妈和女儿一起对主人说话"的互动）
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(daughter)) {
                continue;
            }
            UUID motherId = RelationshipExemption.readMotherUuid(daughter);
            if (motherId == null) {
                continue;
            }
            EntityMaid mother = findMotherIn(maids, daughter);
            if (mother == null || !mother.m_6084_()) {
                continue;
            }
            if (this.motherDaughterDay.getOrDefault(daughter.m_20148_(), -1L) == day) {
                continue;
            }
            this.motherDaughterDay.put(daughter.m_20148_(), day);
            boolean motherTalks = (day + daughter.m_20148_().hashCode()) % 2 == 0;
            EntityMaid speaker = motherTalks ? mother : daughter;
            String prompt = motherTalks ? PromptTexts.MOTHER_TALK : PromptTexts.DAUGHTER_TALK;
            // v1.4.2:无 LLM/配额满 → 固定文本气泡(用户"无 LLM 完整运作"原则)
            DialogueDispatcher.chatWithQuota(speaker, player, prompt,
                    PromptTexts.motherDaughterFallback(speaker.m_7755_().getString(), motherTalks));
        }
    }

    /** 父女互动(v1.2.2;v1.3.0 P4 成年女儿照顾线):女儿对爸爸说一句按成长阶段区分的话(每 2 天 1 次) */
    private void fatherDaughterInteractions(ServerPlayer player, List<EntityMaid> maids, long day) {
        for (EntityMaid daughter : maids) {
            if (!daughter.m_6084_() || !DialogueDispatcher.isOwner(daughter, player)) {
                continue;
            }
            if (!RelationshipExemption.isChild(daughter) || isHurtFeeling(daughter)) {
                continue;
            }
            // v1.5.31：幼儿女儿不会说话——父女互动整个跳过（婴儿不对爸爸说话）
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(daughter)) {
                continue;
            }
            if (this.fatherDaughterDay.getOrDefault(daughter.m_20148_(), -1L) == day) {
                continue;
            }
            this.fatherDaughterDay.put(daughter.m_20148_(), day);
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(daughter);
            if (stage == null) {
                continue;
            }
            // v1.5.53:三人同场变体——妈妈也在场(且未伤心)时,父女互动改一家三口语境
            EntityMaid mother = findMotherIn(maids, daughter);
            boolean familyThree = mother != null && mother.m_6084_()
                    && !isHurtFeeling(mother);
            // v1.3.0 P4:成年女儿单双日交替——奇数轮"陪爸爸聊日常",偶数轮"照顾爸爸"
            if (stage == MaidMarriageCompat.ChildStage.ADULT && day % 4 < 2) {
                // v1.4.2:无 LLM/配额满 → 固定文本气泡
                DialogueDispatcher.chatWithQuota(daughter, player,
                        PromptTexts.adultDaughterCarePrompt(day),
                        PromptTexts.adultDaughterCareFallback(daughter.m_7755_().getString(), day));
            } else if (familyThree) {
                DialogueDispatcher.chatWithQuota(daughter, player,
                        PromptTexts.familyThreePrompt(stage, day),
                        PromptTexts.familyThreeFallback(stage, daughter.m_7755_().getString(), day));
            } else {
                DialogueDispatcher.chatWithQuota(daughter, player,
                        PromptTexts.fatherDaughterPrompt(stage),
                        PromptTexts.fatherDaughterFallback(stage, daughter.m_7755_().getString()));
            }
        }
    }

    /** 在 maids 列表里找 daughter 的妈妈(maidmarriage 血统 UUID 匹配) */
    private static EntityMaid findMotherIn(List<EntityMaid> maids, EntityMaid daughter) {
        UUID motherId = RelationshipExemption.readMotherUuid(daughter);
        if (motherId == null) {
            return null;
        }
        for (EntityMaid m : maids) {
            if (m.m_20148_().equals(motherId)) {
                return m;
            }
        }
        return null;
    }

    /** 成长事件(v1.2.2):女儿成长阶段升级瞬间,触发 LLM 小剧情 + 系统消息 */
    private void checkChildGrowth(ServerPlayer player, List<EntityMaid> maids) {
        for (EntityMaid daughter : maids) {
            if (!daughter.m_6084_() || !DialogueDispatcher.isOwner(daughter, player)) {
                continue;
            }
            if (!RelationshipExemption.isChild(daughter)) {
                continue;
            }
            MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(daughter);
            if (stage == null) {
                continue;
            }
            String stored = daughter.getPersistentData().m_128461_(HeartfeltTags.CHILD_STAGE);
            if (stored == null) {
                // 首次记录(旧档初始化),不触发事件
                daughter.getPersistentData().m_128359_(HeartfeltTags.CHILD_STAGE, stage.name());
                continue;
            }
            if (stored.equals(stage.name())) {
                continue;
            }
            // 阶段升级:记录时间戳 + 系统消息 + LLM 小剧情(配额内)
            daughter.getPersistentData().m_128359_(HeartfeltTags.CHILD_STAGE, stage.name());
            daughter.getPersistentData().m_128356_(HeartfeltTags.CHILD_STAGE_AT,
                    daughter.m_9236_().m_46467_());
            // v1.5.358:JUVENILE 的"站起来"消息移出本方法——标签判定在阶段数据震荡/标签
            // 陈旧时会误发(用户:"摇摇晃晃的站起来随机触发,不符合情境")。站起与消息
            // 统一由 ChildGuardManager 的可靠运行时 INFANT→JUVENILE 检测触发(每 tick
            // 对比实际阶段,只在真实升级瞬间发一次)。这里 JUVENILE 分支只保留标签记录
            // 与站起保险,不再发消息/气泡。
            if (stage == MaidMarriageCompat.ChildStage.JUVENILE) {
                daughter.m_21837_(false); // 保险:确保站起(可靠检测处也会站)
                continue; // 审计 H-7：不能提前退出整个循环，否则同列表后续女儿成长检查被跳过
            }
            player.m_213846_(Component.m_237113_(
                    PromptTexts.growthMessage(daughter.m_7755_().getString(), stage)));
            // v1.5.47:幼女(JUVENILE)成长瞬间——她不会说话,直接发【旁白气泡】
            // (无台词动作描写),绕过 chatWithQuota 的幼儿话语拦截
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(daughter)) {
                daughter.getChatBubbleManager().addTextChatBubble(
                        PromptTexts.growthFallbackToddler(daughter.m_7755_().getString()));
            } else {
                // v1.4.2:LLM 小剧情无 LLM/配额满时,女仆侧降级为固定文本气泡(系统消息已兜底)
                DialogueDispatcher.chatWithQuota(daughter, player,
                        PromptTexts.growthPrompt(stage),
                        PromptTexts.growthFallback(stage, daughter.m_7755_().getString()));
            }
        }
    }

    /** 暗恋者感慨:主人已有妻子后,深爱未告白女仆偶尔释然祝福(纯爱,绝不争风吃醋) */
    private void crushSighs(ServerPlayer player, List<EntityMaid> maids, long day) {
        boolean hasWife = false;
        for (EntityMaid m : maids) {
            if (m.m_6084_() && DialogueDispatcher.isOwner(m, player) && RelationshipExemption.isDedicated(m)) {
                hasWife = true;
                break;
            }
        }
        if (!hasWife) {
            return;
        }
        for (EntityMaid maid : maids) {
            if (!maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                continue;
            }
            if (RelationshipExemption.isDedicated(maid) || !RelationshipExemption.isPartner(maid)) {
                continue; // 只要好感≥192 的深爱暗恋层
            }
            if (isHurtFeeling(maid)) {
                continue;
            }
            if (this.crushDay.getOrDefault(maid.m_20148_(), -1L) == day) {
                continue;
            }
            this.crushDay.put(maid.m_20148_(), day);
            // v1.4.2:无 LLM/配额满 → 固定文本气泡
            DialogueDispatcher.chatWithQuota(maid, player, PromptTexts.CRUSH_SIGH,
                    PromptTexts.crushSighFallback(maid.m_7755_().getString()));
        }
    }

    /** v1.4.1:伤心窗口内暂停主动互动(她坐着赌气,不主动说话)
     *  v1.5.73:改 public——语音拦截 mixin(伤心状态不播语音包)复用 */
    public static boolean isHurtFeeling(EntityMaid maid) {
        return maid.getPersistentData().m_128454_(HeartfeltTags.HURT_UNTIL) > maid.m_9236_().m_46467_();
    }

    // ==================== P5:纪念日 / 回忆杀 ====================

    /**
     * v1.3.0:基于 P1 事件历史(首见/告白/结婚)的纪念日。
     * 当"事件日 → 今天"跨过里程碑(7/30/100/365 天)时,触发一次 LLM 回忆对话
     * ("还记得我们初次见面那天吗…"),让文本回应玩家的过去。每里程碑只触发一次。
     * v1.5.27:触发时追加系统消息——明确"哪个基准事件、第几天纪念日",
     * 不再只是女仆说一句话(玩家可能错过);无 LLM 时气泡降级照常触发。
     */
    private void checkAnniversaries(ServerPlayer player, List<EntityMaid> maids, long day) {
        for (EntityMaid maid : maids) {
            if (!maid.m_6084_() || !DialogueDispatcher.isOwner(maid, player)) {
                continue;
            }
            // v1.5.31：幼儿女儿不会回忆——纪念日（系统消息 + 回忆对话）整个跳过
            if (com.heartfelt.connection.compat.ChildGuardManager.isTooSmall(maid)) {
                continue;
            }
            long milestone = milestoneDue(maid, day);
            if (milestone <= 0L) {
                continue;
            }
            maid.getPersistentData().m_128356_(HeartfeltTags.LAST_ANNIVERSARY_DAY, day);
            // v1.5.48:女儿纪念日父女化——基准=出生/初见,文本按父女口吻
            if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
                player.m_213846_(Component.m_237113_(
                        PromptTexts.anniversarySystemMessageChild(
                                maid.m_7755_().getString(), milestone)));
                DialogueDispatcher.chatWithQuota(maid, player,
                        PromptTexts.anniversaryPrompt(milestone, maid),
                        PromptTexts.anniversaryFallbackChild(milestone, maid.m_7755_().getString()));
                continue;
            }
            // v1.5.27:系统消息明确触发(基准事件 + 天数)——时间类触发"看得见"
            String eventName = maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT) > 0L
                    ? "告白在一起" : "初次相遇";
            player.m_213846_(Component.m_237113_(
                    PromptTexts.anniversarySystemMessage(maid.m_7755_().getString(), milestone, eventName)));
            // v1.4.2:无 LLM/配额满 → 固定文本气泡(按里程碑)
            DialogueDispatcher.chatWithQuota(maid, player,
                    PromptTexts.anniversaryPrompt(milestone, maid),
                    PromptTexts.anniversaryFallback(milestone, maid.m_7755_().getString()));
        }
    }

    /**
     * 距最近事件日的"天数里程碑";到里程碑且本会话未触发过则返回该天数,否则 0。
     * v1.5.12 修复:last 存的是"上次触发的绝对游戏日"(如 5007),旧版与相对里程碑
     * 天数(mark=7/30/100/365)直接比较——基准日 baseDay 较大时首次触发后 last 恒大于
     * mark,30/100/365 里程碑永远无法触发(系统只在开局 24 天内确立关系时完整工作)。
     * 改为与该里程碑对应的绝对触发日(baseDay + mark)比较,大 baseDay 场景恢复正常。
     */
    private static long milestoneDue(EntityMaid maid, long day) {
        long last = maid.getPersistentData().m_128454_(HeartfeltTags.LAST_ANNIVERSARY_DAY);
        // 事件基准:告白 > 首见(取最近发生的关系里程碑)
        long confessionAt = maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT);
        long baseDay;
        if (com.heartfelt.connection.relationship.RelationshipExemption.isChild(maid)) {
            // v1.5.62:女儿纪念日基准=出生/初见——女儿不能告白,彻底无视任何残留的
            // 告白日(CONFESSION_AT,如调整器测试遗留),否则点"首见"后纪念日仍按
            // 告白日计算,弹出"告白类型"的天数/事件
            baseDay = maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET) / 24000L;
        } else {
            baseDay = confessionAt > 0L ? confessionAt / 24000L
                    : maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET) / 24000L;
        }
        if (baseDay <= 0L || day <= baseDay) {
            return 0L;
        }
        long elapsed = day - baseDay;
        long[] marks = {7L, 30L, 100L, 365L};
        long hit = 0L;
        for (long mark : marks) {
            if (elapsed >= mark && last < baseDay + mark) {
                hit = Math.max(hit, mark);
            }
        }
        return hit;
    }
}
