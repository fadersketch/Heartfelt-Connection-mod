package com.heartfelt.connection.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.heartfelt.connection.compat.CallResponseCompat;
import com.heartfelt.connection.compat.MaidMarriageCompat;
import com.heartfelt.connection.compat.ReflectUtil;
import com.heartfelt.connection.debug.HeartfeltDebugApi;
import com.heartfelt.connection.network.HeartfeltNetwork;
import com.heartfelt.connection.prompt.PromptTexts;
import com.heartfelt.connection.relationship.RelationshipExemption;
import com.heartfelt.connection.tags.HeartfeltTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调整器菜单与服务端逻辑(v1.4.3,测试工具)。
 *
 * openMenu:右击女仆 → 聊天栏按钮菜单(标题 + 实时状态行 + 按钮行);
 * applyAction:按钮点击(经 /heartfelt adjust 命令)执行调整,操作后自动刷新菜单。
 *
 * 所有数值读写走现有 compat/原生 API,不改任何原模组源码:
 * - 好感:TLM EntityMaid.getFavorability/setFavorability(直接调)
 * - 心情:MaidMarriageCompat.moodValue/addMood
 * - 信任/恐惧:CallResponseCompat.emotionValues/addTrustFloat/addFearFloat
 * - 关系:MaidMarriageCompat.completeConfession/setMarried/clearMarriage/
 *   setChildState/clearChildState(反射写 maidmarriage TaskData)
 * - 阶段:heartfelt CHILD_STAGE NBT + maidmarriage growth_stage 同步
 * - 记忆/奶计数/清理:heartfelt NBT 直接读写
 */
public final class AdjusterManager {
    private AdjusterManager() {
    }

    /** 打开调整器 GUI(无操作结果) */
    public static void openGui(ServerPlayer player, EntityMaid maid) {
        openGui(player, maid, null);
    }

    /**
     * 打开调整器 GUI(v1.4.6,格式与 Promaid 手册一致;v1.4.10 加 result 反馈;
     * v1.5.7 恢复选择性静止)。
     * 服务端组装实时状态快照 → S2C OpenAdjusterPacket → 客户端 AdjusterScreen 显示;
     * result 为最近一次操作结果文案(GUI 黄色结果行),null 表示无。
     * 同时启动选择性静止(DialogueFreezeManager:其他生物定住,玩家与目标女仆
     * 可动;与 Alt+J 对话面板共用,幂等,刷新不重建)。
     */
    public static void openGui(ServerPlayer player, EntityMaid maid, String result) {
        List<String> lines = new ArrayList<>();
        lines.add(statusLine(maid, player));
        lines.add(statusLine2(maid, player));
        com.heartfelt.connection.dialogue.DialogueFreezeManager.startFreeze(player, maid);
        HeartfeltNetwork.channel().send(PacketDistributor.PLAYER.with(() -> player),
                new HeartfeltNetwork.OpenAdjusterPacket(
                        maid.m_20148_(), maid.m_7755_().getString(), lines, result));
    }

    /**
     * 执行按钮动作(v1.4.9 改返回结果文案):返回操作结果文本(GUI 黄色结果行
     * 即时反馈);null 表示无效动作/关闭(不刷新)。
     */
    public static String applyAction(ServerPlayer player, EntityMaid maid, String action) {
        UUID playerId = player.m_20148_();
        String result;
        switch (action) {
            // ---- 好感 ----
            case "favor+10" -> {
                maid.setFavorability(Mth.m_14045_(maid.getFavorability() + 10, 0, 384));
                result = "好感 +10 → " + maid.getFavorability();
            }
            case "favor-10" -> {
                maid.setFavorability(Mth.m_14045_(maid.getFavorability() - 10, 0, 384));
                result = "好感 -10 → " + maid.getFavorability();
            }
            case "favor=64" -> {
                maid.setFavorability(64);
                result = "好感 = 64";
            }
            case "favor=128" -> {
                maid.setFavorability(128);
                result = "好感 = 128";
            }
            case "favor=192" -> {
                maid.setFavorability(192);
                result = "好感 = 192";
            }
            case "favor=384" -> {
                maid.setFavorability(384);
                result = "好感 = 384";
            }
            // ---- 心情 ----
            case "mood+5" -> {
                MaidMarriageCompat.addMood(maid, 5);
                result = "心情 +5 → " + MaidMarriageCompat.moodValue(maid);
            }
            case "mood-5" -> {
                MaidMarriageCompat.addMood(maid, -5);
                result = "心情 -5 → " + MaidMarriageCompat.moodValue(maid);
            }
            case "mood=15" -> {
                setMoodExact(maid, 15);
                result = "心情 = 15";
            }
            case "mood=25" -> {
                setMoodExact(maid, 25);
                result = "心情 = 25";
            }
            case "mood=5" -> {
                setMoodExact(maid, 5);
                result = "心情 = 5";
            }
            case "mood=10" -> {
                setMoodExact(maid, 10);
                result = "心情 = 10";
            }
            case "mood=20" -> {
                setMoodExact(maid, 20);
                result = "心情 = 20";
            }
            // ---- 信任/恐惧 ----
            case "trust+20" -> {
                CallResponseCompat.addTrustFloat(maid, playerId, 20f);
                result = "信任 +20";
            }
            case "fear-20" -> {
                CallResponseCompat.addFearFloat(maid, playerId, -20f);
                result = "恐惧 -20";
            }
            case "trust=100" -> {
                setTrustExact(maid, playerId, 100);
                result = "信任 = 100";
            }
            case "trust=0" -> {
                setTrustExact(maid, playerId, 0);
                result = "信任 = 0";
            }
            case "fear=0" -> {
                setFearExact(maid, playerId, 0);
                result = "恐惧 = 0";
            }
            case "fear=100" -> {
                setFearExact(maid, playerId, 100);
                result = "恐惧 = 100";
            }
            case "hunger=10" -> {
                CallResponseCompat.hungerSet(maid, 10f);
                result = "饥饿 = 10";
            }
            case "hunger=50" -> {
                CallResponseCompat.hungerSet(maid, 50f);
                result = "饥饿 = 50";
            }
            case "hunger=90" -> {
                CallResponseCompat.hungerSet(maid, 90f);
                result = "饥饿 = 90";
            }
            // ---- 关系 ----
            case "rel=lover" -> {
                MaidMarriageCompat.completeConfession(maid);
                result = "已设为恋人";
            }
            case "rel=wife" -> {
                MaidMarriageCompat.setMarried(maid, playerId, levelTick(maid));
                result = "已设为妻子";
            }
            case "rel=child" -> {
                setChild(maid, playerId);
                result = "已设为女儿";
            }
            case "rel=none" -> {
                clearRelations(maid);
                result = "已解除全部关系";
            }
            // ---- 成长阶段(v1.5.59:4 档——婴儿/幼儿/少女/成年) ----
            // v1.5.354:ticks 改由 stageTicksFor 按 maidmarriage 实时配置计算——旧版写死
            // 48000/120000,在 childGrowthDays 配置较小(如 3 天)时被 maidmarriage 每 tick
            // 重推导成别的阶段(幼儿→少女 / 少女→幼儿),isTooSmall 束缚随之失效。
            case "stage=infant" -> {
                setStage(maid, "INFANT");
                result = "阶段 = 婴儿";
            }
            case "stage=juvenile" -> {
                setStage(maid, "JUVENILE");
                result = "阶段 = 幼儿";
            }
            case "stage=child" -> {
                setStage(maid, "CHILD");
                result = "阶段 = 少女";
            }
            case "stage=adult" -> {
                setStage(maid, "ADULT");
                result = "阶段 = 成年";
            }
            // ---- 特殊奶计数(测三档文本) ----
            case "milk=3" -> {
                maid.getPersistentData().m_128405_(HeartfeltTags.SPECIAL_MILK_COUNT, 3);
                result = "特殊奶计数 = 3";
            }
            // ---- 事件记忆(测纪念日) ----
            case "mem=firstmeet" -> {
                maid.getPersistentData().m_128356_(HeartfeltTags.EVENT_FIRST_MEET, levelTick(maid));
                result = "首见 = 今天";
            }
            case "mem=confession" -> {
                maid.getPersistentData().m_128356_(HeartfeltTags.CONFESSION_AT, levelTick(maid));
                result = "告白 = 今天";
            }
            // ---- v1.5.13 纪念日测试（promaid 联动可测） ----
            case "anniv=7" -> {
                // 首见 = 7 天前 → 7 天里程碑立即到期（不用推时间）
                maid.getPersistentData().m_128356_(HeartfeltTags.EVENT_FIRST_MEET,
                        levelTick(maid) - 7L * 24000L);
                result = "首见 = 7 天前（7 天里程碑到期）";
            }
            case "anniv=reset" -> {
                // 清防重触发标记（heartfelt 侧 + promaid 侧游标）——可重复测试纪念日
                maid.getPersistentData().m_128473_(HeartfeltTags.LAST_ANNIVERSARY_DAY);
                maid.getPersistentData().m_128473_("maid_smart_anniv_mark");
                maid.getPersistentData().m_128473_("maid_smart_anniv_app");
                result = "纪念日防重触发标记已清(heartfelt + promaid)";
            }
            // ---- 清理 ----
            case "clear=hurt" -> {
                clearHurt(maid);
                result = "已清除伤心窗口";
            }
            case "clear=betrayal" -> {
                clearBetrayal(maid, playerId);
                result = "已清除背叛状态";
            }
            // ---- v1.4.4 场景模拟 ----
            case "betray=on" -> {
                setBetrayal(maid);
                result = "已设为背叛状态";
            }
            case "hurt=1d" -> {
                maid.getPersistentData().m_128356_(HeartfeltTags.HURT_UNTIL, levelTick(maid) + 24000L);
                result = "伤心窗口 = 1 天";
            }
            case "wait=mother" -> {
                // 模拟"妈妈被魂符收走":等妈妈标记 + 坐下(ChildGuardManager 的恢复线)
                maid.getPersistentData().m_128379_(HeartfeltTags.WAITING_MOTHER, true);
                maid.m_21837_(true);
                result = "等妈妈标记已设置";
            }
            case "post=1d" -> {
                MaidMarriageCompat.setPostpartum(maid, levelTick(maid), 24000L);
                result = "产后窗口 = 1 天";
            }
            case "grief=child" -> {
                maid.getPersistentData().m_128379_(
                        "maidmarriage_child_loss_grief_active", true);
                result = "已标记丧子哀悼";
            }
            // ---- v1.4.4 实用 ----
            case "debug" -> {
                showDebug(player, maid);
                result = "调试信息已发到聊天栏";
            }
            case "pull" -> {
                maid.m_6034_(player.m_20185_(), player.m_20186_(), player.m_20189_());
                result = "已拉回身边";
            }
            case "pose=1" -> {
                maid.m_21837_(true);
                result = "已坐下";
            }
            case "pose=0" -> {
                maid.m_21837_(false);
                result = "已站起";
            }
            case "tame=on" -> {
                maid.m_21816_(playerId);
                maid.m_7105_(true);
                result = "已认主";
            }
            case "tame=off" -> {
                maid.m_21816_(null);
                result = "已解除认主";
            }
            case "time+1d" -> {
                advanceTime(maid);
                result = "时间 +1 天";
            }
            // v1.5.13：纪念日测试快进（照 time+1d）
            case "time+3d" -> {
                advanceTime(maid);
                advanceTime(maid);
                advanceTime(maid);
                result = "时间 +3 天";
            }
            case "time+7d" -> {
                for (int i = 0; i < 7; i++) {
                    advanceTime(maid);
                }
                result = "时间 +7 天";
            }
            // v1.5.27：纪念日快速快进（测 30/100/365 里程碑）
            case "time+30d" -> {
                for (int i = 0; i < 30; i++) {
                    advanceTime(maid);
                }
                result = "时间 +30 天";
            }
            case "time+100d" -> {
                for (int i = 0; i < 100; i++) {
                    advanceTime(maid);
                }
                result = "时间 +100 天";
            }
            // ---- v1.4.4 关系细节 ----
            case "fail=1" -> {
                maid.getPersistentData().m_128379_(HeartfeltTags.CONFESSION_FAILED, true);
                maid.getPersistentData().m_128356_(HeartfeltTags.CONFESSION_FAILED_AT, levelTick(maid));
                result = "已标记告白失败";
            }
            case "broken=1" -> {
                maid.getPersistentData().m_128356_(HeartfeltTags.HEARTBROKEN_AT, levelTick(maid));
                result = "已标记心碎";
            }
            case "long=1d" -> {
                // v1.5.4:主效果 = 设 mood_data.lastInteractionDay 为 3 天前
                // (isLongingForInteraction → 靠近时心形粒子+思慕对话);
                // forceLonging 顺带改对话变量(lastRomanceDay)
                MaidMarriageCompat.setLonging(maid, levelTick(maid));
                MaidMarriageCompat.setLongingInteraction(maid, levelTick(maid));
                player.m_213846_(Component.m_237113_(
                        PromptTexts.longingSetMessage(maid.m_7755_().getString())));
                result = "思慕 = 3 天没亲近";
            }
            case "break=1" -> {
                maid.getPersistentData().m_128405_(HeartfeltTags.EVENT_BREAKUP_COUNT, 1);
                player.m_213846_(Component.m_237113_(
                        PromptTexts.breakupSetMessage(maid.m_7755_().getString())));
                result = "破裂次数 = 1";
            }
            case "close" -> {
                return null;
            }
            default -> {
                return null;
            }
        }
        // 女仆气泡反馈(有 LLM 也不走对话——测试工具,保持安静可控)
        maid.getChatBubbleManager().addTextChatBubble("……主人？");
        return result;
    }

    // ==================== 状态快照(GUI 显示用) ====================

    /** 数值状态:好感/心情/信任/恐惧 */
    private static String statusLine(EntityMaid maid, ServerPlayer player) {
        int favor = maid.getFavorability();
        int mood = MaidMarriageCompat.moodValue(maid);
        int[] emotion = CallResponseCompat.emotionValues(maid, player.m_20148_());
        String trust = emotion == null ? "?" : String.valueOf(emotion[0]);
        String fear = emotion == null ? "?" : String.valueOf(emotion[1]);
        return "\u00a7f好感 " + favor + "/384 \u00a78|\u00a7f 心情 "
                + (mood < 0 ? "?" : mood) + "/25 \u00a78|\u00a7f 信任 " + trust
                + " \u00a78|\u00a7f 恐惧 " + fear;
    }

    /** 关系状态:关系/阶段/奶/标记 */
    private static String statusLine2(EntityMaid maid, ServerPlayer player) {
        String rel = RelationshipExemption.relationLabel(maid);
        if (rel == null) {
            rel = "普通";
        }
        MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
        String stageStr = stage == null ? "—" : switch (stage) {
            case INFANT -> "婴儿";
            case JUVENILE -> "幼儿";
            case CHILD -> "少女";
            case ADULT -> "成年";
        };
        int milk = maid.getPersistentData().m_128451_(HeartfeltTags.SPECIAL_MILK_COUNT);
        StringBuilder flags = new StringBuilder();
        if (maid.getPersistentData().m_128454_(HeartfeltTags.HURT_UNTIL) > levelTick(maid)) {
            flags.append(" \u00a7c伤心");
        }
        if (CallResponseCompat.isBetraying(maid)) {
            flags.append(" \u00a7c背叛");
        }
        if (MaidMarriageCompat.isLongingForInteraction(maid)) {
            flags.append(" \u00a7e思慕中");
        }
        // v1.5.13：纪念日里程碑（基准=告白/初遇；已达成取 promaid 游标与 heartfelt
        // 上次触发日的较大者——promaid 联动状态直接可读，零依赖）
        // v1.5.27：时间线明确化——显示基准事件名 + 已达成 + 下个里程碑倒计时
        long confessionAt = maid.getPersistentData().m_128454_(HeartfeltTags.CONFESSION_AT);
        long firstMeetAt = maid.getPersistentData().m_128454_(HeartfeltTags.EVENT_FIRST_MEET);
        long baseDay = confessionAt > 0L ? confessionAt / 24000L
                : (firstMeetAt > 0L ? firstMeetAt / 24000L : 0L);
        if (baseDay > 0L) {
            long day = levelTick(maid) / 24000L;
            long elapsed = day - baseDay;
            long pmMark = maid.getPersistentData().m_128454_("maid_smart_anniv_mark");
            long hfMark = maid.getPersistentData().m_128454_(HeartfeltTags.LAST_ANNIVERSARY_DAY);
            long done = Math.max(pmMark, hfMark > baseDay ? hfMark - baseDay : 0L);
            String baseName = confessionAt > 0L ? "告白" : "首见";
            flags.append(" \u00a7e纪念日[基准" + baseName + "]" + elapsed + "天");
            if (done > 0L) {
                flags.append("·达成" + done);
            }
            // 下个里程碑倒计时(7/30/100/365)
            long[] marks = {7L, 30L, 100L, 365L};
            for (long mark : marks) {
                if (mark > done) {
                    long left = mark - elapsed;
                    if (left > 0L) {
                        flags.append("\u00a7a·" + mark + "天还差" + left + "天");
                    } else {
                        flags.append("\u00a7e·" + mark + "天已到期");
                    }
                    break;
                }
            }
        }
        return "\u00a7f关系 " + rel + " \u00a78|\u00a7f 阶段 " + stageStr
                + " \u00a78|\u00a7f 奶×" + milk + flags;
    }

    // ==================== 私有动作 ====================

    private static long levelTick(EntityMaid maid) {
        return maid.m_9236_().m_46467_();
    }

    /** v1.5.43:server tick 字段缓存(反射识别:MinecraftServer 中值 == getTickCount 的 int 字段) */
    private static java.lang.reflect.Field TICK_COUNT_FIELD = null;

    /**
     * 时间快进 1 游戏日(v1.4.6 GUI 版;v1.5.43 修复——旧版执行 /time add 24000
     * 只改 dayTime(昼夜循环),gameTime(纪念日/日期逻辑的时钟)不变 → 调试器时间
     * 快进对纪念日完全无效,日期功能测不了)。改为直接推进三个时钟:
     * ① gameTime(PrimaryLevelData.f_78450_——milestoneDue/纪念日/成长/窗口都用它);
     * ② dayTime(f_78451_——昼夜同步,保持日夜一致);
     * ③ server tick(MinecraftServer tickCount——FamilyInteractionManager 的
     *    每日检查用 server.m_129921_())。
     */
    private static void advanceTime(EntityMaid maid) {
        if (!(maid.m_9236_() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.m_7654_();
        if (server == null) {
            return;
        }
        try {
            // Level.f_46442_(WritableLevelData,运行时 PrimaryLevelData)反射访问
            java.lang.reflect.Field lf = net.minecraft.world.level.Level.class.getDeclaredField("f_46442_");
            lf.setAccessible(true);
            Object data = lf.get(level);
            if (data == null) {
                return;
            }
            java.lang.reflect.Field gf = net.minecraft.world.level.storage.PrimaryLevelData.class
                    .getDeclaredField("f_78450_"); // gameTime
            gf.setAccessible(true);
            java.lang.reflect.Field df = net.minecraft.world.level.storage.PrimaryLevelData.class
                    .getDeclaredField("f_78451_"); // dayTime
            df.setAccessible(true);
            gf.setLong(data, gf.getLong(data) + 24000L);
            df.setLong(data, df.getLong(data) + 24000L);
            // server tick 同步推进(FamilyInteractionManager 的每日检查用)
            java.lang.reflect.Field tf = tickCountField(server);
            if (tf != null) {
                tf.setInt(server, server.m_129921_() + 24000);
            }
        } catch (Exception ignored) {
        }
    }

    /** 识别 MinecraftServer.tickCount 字段(值 == getTickCount 的 int 字段;首次调用缓存) */
    private static java.lang.reflect.Field tickCountField(MinecraftServer server) {
        if (TICK_COUNT_FIELD == null) {
            for (java.lang.reflect.Field f : MinecraftServer.class.getDeclaredFields()) {
                if (f.getType() != int.class) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    if (f.getInt(server) == server.m_129921_()) {
                        TICK_COUNT_FIELD = f;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return TICK_COUNT_FIELD;
    }

    /** 心情绝对值(0..25):差值调用 addMood */
    private static void setMoodExact(EntityMaid maid, int target) {
        int current = MaidMarriageCompat.moodValue(maid);
        if (current < 0) {
            return;
        }
        MaidMarriageCompat.addMood(maid, target - current);
    }

    /** 设为女儿:写 maidmarriage ChildStateData + heartfelt 阶段 NBT(阶段保持当前,默认幼年) */
    private static void setChild(EntityMaid maid, UUID fatherId) {
        MaidMarriageCompat.ChildStage stage = MaidMarriageCompat.childStage(maid);
        String stageName;
        int ticks;
        if (stage == MaidMarriageCompat.ChildStage.CHILD) {
            stageName = "CHILD";
            ticks = 5 * 24000;
        } else if (stage == MaidMarriageCompat.ChildStage.ADULT) {
            stageName = "ADULT";
            ticks = 999999;
        } else {
            stageName = "INFANT";
            ticks = 0;
        }
        if (!MaidMarriageCompat.setChildState(maid, stageName, ticks, fatherId)) {
            return;
        }
        maid.getPersistentData().m_128359_(HeartfeltTags.CHILD_STAGE, stageName);
    }

    /** 阶段切换(调试器成长测试):同步 maidmarriage growth_stage/growth_ticks。
     *  v1.5.76:【不再预同步 heartfelt CHILD_STAGE】——旧版直接把标签设成新阶段,
     *  checkChildGrowth 看到 stored==stage 直接跳过,成长事件永不触发(用户:"站起来
     *  这句话的出现时机有问题,不是从婴儿到幼儿触发"——用调试器 婴儿→幼儿 测试时
     *  事件被吞)。不设标签 → 下一 tick checkChildGrowth 检测到变化,按新阶段触发
     *  事件文本 + 站起动作(婴儿→幼儿 = "会自己站起来了"+ 真正站起)。 */
    private static void setStage(EntityMaid maid, String stageName) {
        MaidMarriageCompat.setChildState(maid, stageName,
                MaidMarriageCompat.stageTicksFor(stageName), ownerId(maid));
    }

    /** 清伤心窗口:解除标记 + 恢复站姿(非等妈妈状态) */
    private static void clearHurt(EntityMaid maid) {
        maid.getPersistentData().m_128473_(HeartfeltTags.HURT_UNTIL);
        maid.getPersistentData().m_128473_(HeartfeltTags.HURT_PENALTY_DAY);
        maid.getPersistentData().m_128473_(HeartfeltTags.HURT_PENALTY_COUNT);
        if (maid.isMaidInSittingPose() && !maid.getPersistentData().m_128471_(HeartfeltTags.WAITING_MOTHER)) {
            maid.m_21837_(false);
        }
    }

    /** 解除全部关系:婚姻/告白/女儿(彻底)+ heartfelt 阶段标记 */
    private static void clearRelations(EntityMaid maid) {
        MaidMarriageCompat.clearMarriage(maid);
        MaidMarriageCompat.resetConfession(maid);
        clearChildStateFully(maid);
        maid.getPersistentData().m_128473_(HeartfeltTags.CHILD_STAGE);
        maid.getPersistentData().m_128473_(HeartfeltTags.CHILD_STAGE_AT);
        // v1.5.66:解除关系后同步清除玩家的"确认关系"压制标记(heartfelt_dedicated)——
        // 否则吃醋隔离/告白压制仍生效,其他女仆继续受已解除的结婚关系影响;
        // 仅当该玩家已无其他确认关系女仆时清除(与 v1.5.19 破裂语义一致)
        if (maid.m_269323_() instanceof net.minecraft.server.level.ServerPlayer owner) {
            com.heartfelt.connection.relationship.RelationshipExemption.clearDedicatedIfNone(owner);
        }
    }

    /**
     * 彻底解除女儿状态(v1.4.9 修"永久覆盖"):TaskData EMPTY + 清全部 persistent
     * 女儿/血统标记。
     *
     * 原实现只清 TaskData(child_state_data → EMPTY),但 maidmarriage 的
     * MaidWorkManager.onMaidTick(MaidTickEvent,每 tick)会调
     * tickExternalChildLifecycle:isBornMaid 含 shouldStayChild 判定,而
     * shouldStayChild 在 TaskData child=false 时读 persistent 残留
     * (child_active/growth_ticks/growth_stage)→ 每 tick 从 persistent 恢复
     * TaskData child=true——"女儿状态永久覆盖"。
     *
     * 仿 maidmarriage markAsAdult(官方解除 API)清理 persistent,并额外清
     * 血统 UUID 与 born tag(isBornMaid 的另一判定源),彻底回到普通女仆。
     */
    private static void clearChildStateFully(EntityMaid maid) {
        // 优先走 maidmarriage 官方解除 API(清 persistent 女儿标记 + TaskData EMPTY,保留名字)
        Method markAdult = ReflectUtil.staticMethod(
                "com.example.maidmarriage.entity.MaidChildEntity", "markAsAdult", EntityMaid.class);
        if (markAdult != null) {
            ReflectUtil.invokeStatic(markAdult, maid);
        }
        MaidMarriageCompat.clearChildState(maid); // 兜底:TaskData → EMPTY
        net.minecraft.nbt.CompoundTag persistent = maid.getPersistentData();
        persistent.m_128379_("maidmarriage_child_active", false);
        persistent.m_128473_("maidmarriage_child_growth_ticks");
        persistent.m_128473_("maidmarriage_child_growth_stage");
        persistent.m_128473_("maidmarriage_infant_carry_end_tick");
        persistent.m_128473_("maidmarriage_child_tame_initialized");
        // 血统引用与出生标记:isBornMaid 的判定源,残留仍被视为 born maid
        persistent.m_128473_("maidmarriage_mother_uuid");
        persistent.m_128473_("maidmarriage_father_uuid");
        persistent.m_128473_("maidmarriage_grand_parent_uuid");
        // Entity 无公开 removeTag;getTags() 返回可变 Set,移除后保存时自动写回 NBT
        maid.m_19880_().remove("maidmarriage_born_maid");
    }

    /**
     * 清背叛(v1.5.1 修复):解除爱憎分明背叛 + 【重新认主站起】+ 清 heartfelt
     * 仇恨/悔改标记。
     * 背叛时 triggerBetrayal 会 setOwnerUUID(null) 解除认主,而爱憎分明的
     * resetBetrayal 不恢复——只调 resetBetrayal 会导致女仆无主、无法互动。
     */
    private static void clearBetrayal(EntityMaid maid, UUID playerId) {
        CallResponseCompat.resetBetrayal(maid);
        // 补:重新认主 + 站起(背叛时解除认主,不补则女仆无主无法互动)
        maid.m_21816_(playerId);
        maid.m_7105_(true);
        maid.m_21837_(false);
        maid.getPersistentData().m_128473_(HeartfeltTags.HATED_PLAYER);
        maid.getPersistentData().m_128473_(HeartfeltTags.HATED_AT);
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_PROGRESS);
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_BETRAYED_AT);
        maid.getPersistentData().m_128473_(HeartfeltTags.REDEMPTION_REDEMPTOR);
    }

    // ==================== v1.4.4:场景模拟 / 数值精度 / 实用 ====================

    /** 设背叛:写爱憎分明 IsBetraying NBT(持久判定源)+ 解除认主,与 triggerBetrayal 等价 */
    private static void setBetrayal(EntityMaid maid) {
        maid.getPersistentData().m_128379_("IsBetraying", true);
        maid.m_21816_(null);
        maid.getPersistentData().m_128356_(HeartfeltTags.REDEMPTION_BETRAYED_AT, levelTick(maid));
    }

    /** 信任绝对值(0..100):差值调用 */
    private static void setTrustExact(EntityMaid maid, UUID playerId, int target) {
        int[] emotion = CallResponseCompat.emotionValues(maid, playerId);
        if (emotion == null) {
            return;
        }
        CallResponseCompat.addTrustFloat(maid, playerId, (float) (target - emotion[0]));
    }

    /** 恐惧绝对值(0..100):差值调用 */
    private static void setFearExact(EntityMaid maid, UUID playerId, int target) {
        int[] emotion = CallResponseCompat.emotionValues(maid, playerId);
        if (emotion == null) {
            return;
        }
        CallResponseCompat.addFearFloat(maid, playerId, (float) (target - emotion[1]));
    }

    /** 调试信息:显示 HeartfeltDebugApi.maidDebug 全量状态行 */
    private static void showDebug(ServerPlayer player, EntityMaid maid) {
        String[] lines = HeartfeltDebugApi.maidDebug(maid);
        if (lines == null || lines.length == 0) {
            return;
        }
        for (String line : lines) {
            player.m_213846_(Component.m_237113_("\u00a78" + line));
        }
    }

    /** 阶段切换/女儿标记用:主人 UUID(无则玩家随机 UUID,仅占位) */
    private static UUID ownerId(EntityMaid maid) {
        net.minecraft.world.entity.LivingEntity owner = maid.m_269323_();
        return owner != null ? owner.m_20148_() : UUID.randomUUID();
    }
}
