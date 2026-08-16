package com.heartfelt.connection.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Heartfelt-connection 配置(v1.1.0 新增)。
 *
 * 把散落的魔法数收敛为可配置项;改动即时生效需重启(Forge ConfigSpec 常规行为)。
 * 注册:HeartfeltMod 构造器 `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HeartfeltConfig.SPEC)`
 */
public final class HeartfeltConfig {
    public static final ForgeConfigSpec SPEC;

    // ---- A2:仇恨标记留存 ----
    /** heartfelt_hated_player 保留的游戏日;超期自动清除(修永久残留) */
    public static final ForgeConfigSpec.IntValue HATED_PLAYER_RETENTION_DAYS;

    // ---- A4:恐惧→好感扣减 ----
    /** 冻结层女仆因恐惧扣好感的每日上限(0 = 不设限) */
    public static final ForgeConfigSpec.IntValue FEAR_FAVOR_DAILY_CAP;

    // ---- 背叛悔改 ----
    /** 悔改所需安抚次数 */
    public static final ForgeConfigSpec.IntValue REDEMPTION_FEEDS;
    /** 每次安抚降低的恐惧值 */
    public static final ForgeConfigSpec.DoubleValue REDEMPTION_FEAR_DROP;
    /** 每次安抚提升的信任值 */
    public static final ForgeConfigSpec.DoubleValue REDEMPTION_TRUST_GAIN;
    /** 安抚中断多久(游戏日)进度清零 */
    public static final ForgeConfigSpec.IntValue REDEMPTION_STALE_DAYS;

    // ---- v1.2.0:冻结折算与关系破裂 ----
    /** 确认关系(恋人)冻结所需的最低好感(默认 128=告白线;跌破即破裂) */
    public static final ForgeConfigSpec.IntValue FREEZE_CONFESSION_LINE;
    /** 玩家造成的信任变化 → 好感折算比率(1.0 = 1:1;负向同样折算) */
    public static final ForgeConfigSpec.DoubleValue TRUST_FAVOR_RATIO;
    /** 玩家造成的恐惧变化 → 好感折算比率(正值=扣,负值=加;0.5 = 2:1) */
    public static final ForgeConfigSpec.DoubleValue FEAR_FAVOR_RATIO;

    // ---- v1.2.0:女仆主动告白 ----
    /** 主动告白的尝试窗口间隔(tick,默认 2400=2min) */
    public static final ForgeConfigSpec.IntValue CONFESSION_ATTEMPT_INTERVAL;
    /** 好感 192 时的基础尝试概率(0.0~1.0) */
    public static final ForgeConfigSpec.DoubleValue CONFESSION_BASE_CHANCE;
    /** 好感 192→384 的线性概率加成(0.0~1.0,满好感时 = base+bonus) */
    public static final ForgeConfigSpec.DoubleValue CONFESSION_FAVOR_BONUS;
    /** 尝试前威胁检查半径(格;半径内无敌对生物且女仆不在战斗中才发起) */
    public static final ForgeConfigSpec.IntValue CONFESSION_THREAT_RADIUS;
    /** 女仆与主人的最大触发距离(格) */
    public static final ForgeConfigSpec.IntValue CONFESSION_MAX_DISTANCE;
    /** 拒绝告白的心情扣减 */
    public static final ForgeConfigSpec.IntValue CONFESSION_FAIL_MOOD;
    /** 主动告白需要的触发好感(默认 192) */
    public static final ForgeConfigSpec.IntValue CONFESSION_REQUIRED_FAVOR;

    // ---- v1.5.0:告白前摇(威胁检测 → 系统消息 → 走向玩家 → 到身边触发) ----
    /** 前摇开关:开启后女仆先走向玩家,走到身边才拉出告白选项 */
    public static final ForgeConfigSpec.BooleanValue CONFESSION_APPROACH_ENABLED;
    /** 前摇到达判定:距玩家多少格内视为"走到身边"(格) */
    public static final ForgeConfigSpec.DoubleValue CONFESSION_APPROACH_DISTANCE;
    /** 前摇超时(tick,默认 1200=60s):没走到/被打断则取消 */
    public static final ForgeConfigSpec.IntValue CONFESSION_APPROACH_TIMEOUT;
    /** 前摇走向玩家的速度倍率 */
    public static final ForgeConfigSpec.DoubleValue CONFESSION_APPROACH_SPEED;
    /** v1.5.8:Alt+J 对话面板的安全区半径(格;半径内无敌对生物才能打开对话) */
    public static final ForgeConfigSpec.IntValue DIALOGUE_SAFE_RADIUS;
    /** v1.5.17:告白前摇最短间隔(tick,默认 50=2.5s)——系统消息发出后至少等这么久
     *  才拉告白选项,女仆已在身边时不至于第一秒就触发、太突兀 */
    public static final ForgeConfigSpec.IntValue CONFESSION_APPROACH_MIN_TICKS;

    // ---- v1.2.1:好感度对信任/恐惧的中和缓和 ----
    /** 中和缓和速率(每秒;满好感时 信任+rate、恐惧-rate;0=关闭) */
    public static final ForgeConfigSpec.DoubleValue EMOTION_SMOOTH_RATE_PER_SECOND;
    /** 中和缓和批量间隔(tick,默认 100=5 秒) */
    public static final ForgeConfigSpec.IntValue EMOTION_SMOOTH_BATCH_TICKS;

    // ---- A10:行为与扫描 ----
    /** 反击背叛者行为的核心行为优先级(低于自保 250,高于 TLM core 最高 99) */
    public static final ForgeConfigSpec.IntValue BETRAYER_BRAIN_PRIORITY;
    /** 背叛者扫描间隔(tick) */
    public static final ForgeConfigSpec.IntValue BETRAYER_SCAN_INTERVAL;

    // ---- 对话触发频率 ----
    /** 关系广播扫描间隔(tick,默认 400=20s) */
    public static final ForgeConfigSpec.IntValue BROADCAST_SCAN_INTERVAL;
    /** 家庭互动扫描间隔(tick,默认 2400=2min) */
    public static final ForgeConfigSpec.IntValue FAMILY_SCAN_INTERVAL;
    /** 怀孕饥饿钳制扫描间隔(tick,默认 100=5s) */
    public static final ForgeConfigSpec.IntValue PREGNANCY_SCAN_INTERVAL;

    // ---- v1.2.2:女儿线拓展 ----
    /** 父女互动(奇数日,与母女交替)开关 */
    public static final ForgeConfigSpec.BooleanValue FATHER_DAUGHTER_ENABLED;
    /** 女儿成长阶段升级事件(LLM 小剧情+系统消息)开关 */
    public static final ForgeConfigSpec.BooleanValue GROWTH_EVENT_ENABLED;

    // ---- v1.3.0:P0 告白方向修正 ----
    /** 玩家主动告白走 heartfelt 自己的告白屏(拦截 maidmarriage 告白剧本) */
    public static final ForgeConfigSpec.BooleanValue PLAYER_CONFESSION_ENABLED;

    // ---- v1.4.1:玩家伤害惩罚(关系不破坏,但有后果) ----
    /** 触发惩罚所需伤害次数(30 秒窗口内对同一女仆;防误伤豁免) */
    public static final ForgeConfigSpec.IntValue HARM_TRIGGER_HITS;
    /** 每次惩罚扣除的心情 */
    public static final ForgeConfigSpec.IntValue HARM_MOOD_DROP;
    /** 每日同一女仆最多惩罚次数(防刷) */
    public static final ForgeConfigSpec.IntValue HARM_DAILY_CAP;
    /** 伤心窗口时长(tick,默认 24000=1 游戏日) */
    public static final ForgeConfigSpec.IntValue HARM_FEELING_TICKS;
    /** 惩罚时是否尝试 LLM 伤心对话(无 LLM/配额满自动降级为固定文本) */
    public static final ForgeConfigSpec.BooleanValue HARM_LLM_REACTION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("hatedPlayer");
        HATED_PLAYER_RETENTION_DAYS = builder
                .comment("heartfelt_hated_player 保留天数(游戏日);超期自动清除")
                .defineInRange("retentionDays", 7, 1, 120);
        builder.pop();

        builder.push("fearFavor");
        FEAR_FAVOR_DAILY_CAP = builder
                .comment("冻结层女仆因恐惧每日最多扣好感(0=不设限)——防战斗流悄悄跌破婚姻线")
                .defineInRange("dailyCap", 8, 0, 192);
        builder.pop();

        builder.push("redemption");
        REDEMPTION_FEEDS = builder
                .comment("背叛悔改所需安抚(喂食)次数(默认 3——v1.5.1 从 5 下调,正常玩家好准备)")
                .defineInRange("feeds", 3, 1, 30);
        REDEMPTION_FEAR_DROP = builder
                .comment("每次安抚降低的恐惧值")
                .defineInRange("fearDrop", 8.0, 1.0, 30.0);
        REDEMPTION_TRUST_GAIN = builder
                .comment("每次安抚提升的信任值")
                .defineInRange("trustGain", 4.0, 1.0, 30.0);
        REDEMPTION_STALE_DAYS = builder
                .comment("安抚中断多少游戏日进度清零")
                .defineInRange("staleDays", 3, 1, 30);
        builder.pop();

        builder.push("freeze");
        FREEZE_CONFESSION_LINE = builder
                .comment("恋人冻结所需最低好感(=告白线 128);跌破即关系破裂、冻结解除")
                .defineInRange("confessionLine", 128, 64, 192);
        TRUST_FAVOR_RATIO = builder
                .comment("玩家造成的信任变化 → 好感折算比率(1.0=1:1;正负同向)")
                .defineInRange("trustFavorRatio", 1.0, 0.0, 2.0);
        FEAR_FAVOR_RATIO = builder
                .comment("玩家造成的恐惧变化 → 好感折算比率(恐惧+扣好感/恐惧-加好感;0.5=2:1)")
                .defineInRange("fearFavorRatio", 0.5, 0.0, 2.0);
        builder.pop();

        builder.push("confession");
        CONFESSION_ATTEMPT_INTERVAL = builder
                .comment("女仆主动告白的尝试窗口间隔(tick,默认 2400=2min)")
                .defineInRange("attemptInterval", 2400, 400, 24000);
        CONFESSION_BASE_CHANCE = builder
                .comment("好感=CONFESSION_REQUIRED_FAVOR 时的基础尝试概率(0.0~1.0;v1.5.17 0.15→0.20 适度放宽)")
                .defineInRange("baseChance", 0.20, 0.0, 1.0);
        CONFESSION_FAVOR_BONUS = builder
                .comment("好感 192→384 的线性概率加成(满好感时 = base+bonus)")
                .defineInRange("favorBonus", 0.45, 0.0, 1.0);
        CONFESSION_THREAT_RADIUS = builder
                .comment("尝试前威胁检查半径(格;v1.5.17 48→24——旧版半径过大,正常世界几乎总有怪,导致很难触发):半径内无敌对生物且女仆不在战斗中才发起")
                .defineInRange("threatRadius", 24, 8, 128);
        CONFESSION_MAX_DISTANCE = builder
                .comment("女仆与主人的最大触发距离(格);太远则暂缓(关窗同此,稍后再试)")
                .defineInRange("maxDistance", 32, 4, 128);
        CONFESSION_FAIL_MOOD = builder
                .comment("拒绝告白的心情扣减(0=不扣)")
                .defineInRange("failMood", 6, 0, 25);
        CONFESSION_REQUIRED_FAVOR = builder
                .comment("主动告白需要的触发好感(默认 192=恋爱线)")
                .defineInRange("requiredFavor", 192, 128, 384);
        // v1.5.0:告白前摇(威胁检测 → 系统消息 → 走向玩家 → 到身边触发)
        CONFESSION_APPROACH_ENABLED = builder
                .comment("告白前摇开关:开启后女仆先走向玩家,走到身边才拉出告白选项")
                .define("approachEnabled", true);
        CONFESSION_APPROACH_DISTANCE = builder
                .comment("前摇到达判定:女仆距玩家多少格内视为走到身边(格;上限 2.0——" +
                        "maidmarriage 交互距离门槛为 2.25 格,必须在其内才能弹出告白界面)")
                .defineInRange("approachDistance", 2.0, 1.0, 2.0);
        CONFESSION_APPROACH_TIMEOUT = builder
                .comment("前摇超时(tick,默认 1200=60s):没走到/被打断则取消")
                .defineInRange("approachTimeout", 1200, 200, 12000);
        CONFESSION_APPROACH_SPEED = builder
                .comment("前摇走向玩家的速度倍率")
                .defineInRange("approachSpeed", 1.0, 0.2, 2.0);
        CONFESSION_APPROACH_MIN_TICKS = builder
                .comment("前摇最短间隔(tick,默认 50=2.5s):系统消息发出后至少等这么久才拉告白选项(女仆已在身边时不至于第一秒就触发)")
                .defineInRange("approachMinTicks", 50, 20, 200);
        // v1.5.8:Alt+J 对话安全区
        DIALOGUE_SAFE_RADIUS = builder
                .comment("Alt+J 对话面板安全区半径(格):玩家与女仆半径内无敌对生物才能打开对话")
                .defineInRange("dialogueSafeRadius", 32, 8, 128);
        builder.pop();

        builder.push("emotionSmooth");
        EMOTION_SMOOTH_RATE_PER_SECOND = builder
                .comment("好感度对信任/恐惧的中和缓和速率(每秒;满好感时 信任+rate、恐惧-rate;0=关闭)")
                .defineInRange("ratePerSecond", 0.2, 0.0, 2.0);
        EMOTION_SMOOTH_BATCH_TICKS = builder
                .comment("中和缓和批量间隔(tick,默认 100=5 秒)")
                .defineInRange("batchTicks", 100, 20, 600);
        builder.pop();

        builder.push("behavior");
        BETRAYER_BRAIN_PRIORITY = builder
                .comment("反击背叛者行为优先级(低于自保 250;与同模块同优先级会互抢槽位)")
                .defineInRange("brainPriority", 150, 100, 200);
        BETRAYER_SCAN_INTERVAL = builder
                .comment("反击背叛者扫描间隔(tick,默认 40=2s)")
                .defineInRange("scanInterval", 40, 10, 200);
        builder.pop();

        builder.push("dialogue");
        BROADCAST_SCAN_INTERVAL = builder
                .comment("关系广播扫描间隔(tick,默认 400=20s)")
                .defineInRange("broadcastInterval", 400, 100, 2400);
        FAMILY_SCAN_INTERVAL = builder
                .comment("家庭互动扫描间隔(tick,默认 2400=2min)")
                .defineInRange("familyInterval", 2400, 400, 12000);
        PREGNANCY_SCAN_INTERVAL = builder
                .comment("怀孕饥饿钳制扫描间隔(tick,默认 100=5s)")
                .defineInRange("pregnancyInterval", 100, 20, 600);
        FATHER_DAUGHTER_ENABLED = builder
                .comment("父女互动(奇数日,与母女交替;v1.2.2)")
                .define("fatherDaughterEnabled", true);
        GROWTH_EVENT_ENABLED = builder
                .comment("女儿成长阶段升级事件(LLM 小剧情+系统消息;v1.2.2)")
                .define("growthEventEnabled", true);
        PLAYER_CONFESSION_ENABLED = builder
                .comment("玩家主动告白走 heartfelt 自己的告白屏(拦截 maidmarriage 告白剧本;" +
                        "v1.3.0 P0 告白方向修正)——关掉则回退 maidmarriage 原剧本")
                .define("playerConfessionEnabled", true);
        builder.pop();

        builder.push("harmPenalty");
        HARM_TRIGGER_HITS = builder
                .comment("触发惩罚所需伤害次数(30 秒窗口内对同一女仆;1 次误伤豁免," +
                        "连续伤害才判故意;v1.4.1)")
                .defineInRange("triggerHits", 3, 2, 10);
        HARM_MOOD_DROP = builder
                .comment("每次惩罚扣除的心情(0=不扣,只留伤心窗口)")
                .defineInRange("moodDrop", 5, 0, 25);
        HARM_DAILY_CAP = builder
                .comment("每日同一女仆最多惩罚次数(防刷;0=不设限)")
                .defineInRange("dailyCap", 2, 0, 10);
        HARM_FEELING_TICKS = builder
                .comment("伤心窗口时长(tick,默认 24000=1 游戏日;窗口内她坐着赌气、"
                        + "不主动互动、对话注入委屈文本)")
                .defineInRange("feelingTicks", 24000, 1200, 240000);
        HARM_LLM_REACTION = builder
                .comment("惩罚时尝试 LLM 伤心对话(需配置 LLM + 配额;无 LLM 自动降级为固定文本)")
                .define("llmReaction", true);
        builder.pop();

        SPEC = builder.build();
    }

    private HeartfeltConfig() {
    }
}
