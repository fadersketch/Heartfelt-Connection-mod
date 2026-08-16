package com.heartfelt.connection.tags;

/**
 * 共享常量(v1.1.0 全面重构)——NBT 键 / mod id / 包名前缀统一收敛于此。
 * heartfelt 各模块读写同一份 ForgeData 键,杜绝裸字符串漂移。
 */
public final class HeartfeltTags {
    private HeartfeltTags() {
    }

    // ---------- 依赖 mod id ----------

    /** maidmarriage 心契誓约 */
    public static final String MM_ID = "maidmarriage";
    /** callresponse 爱憎分明(真实包名前缀,非 com.github.tartaricacid) */
    public static final String CR_PACKAGE = "com.github.JumDa5he.callresponse.compat";
    /** maidmarriage 的 compat 包 */
    public static final String MM_COMPAT = "com.example.maidmarriage.compat";

    // ---------- 背叛 ----------

    /** 背叛瞬间记录的"被恨的玩家"UUID(EntityMaid ForgeData)——背叛后 owner 被清空,靠它找主人珍视的人 */
    public static final String HATED_PLAYER = "heartfelt_hated_player";

    /** 背叛发生时刻(游戏 tick)——A2:超期自动清除仇恨标记 */
    public static final String HATED_AT = "heartfelt_hated_at";

    // ---------- 悼念 ----------

    /** 主人死亡哀悼截止游戏 tick(EntityMaid ForgeData)——哀悼期内拒绝亲密互动 */
    public static final String MOURNING_UNTIL = "heartfelt_mourning_until";

    // ---------- 悔改(与 BetrayalRedemptionManager 共享) ----------

    /** 安抚进度(喂食次数) */
    public static final String REDEMPTION_PROGRESS = "heartfelt_redemption_progress";
    /** 首次安抚时刻(游戏 tick)——超时进度清零 */
    public static final String REDEMPTION_BETRAYED_AT = "heartfelt_betrayed_at";
    /** 首个安抚者(未来重新认主的对象)UUID */
    public static final String REDEMPTION_REDEMPTOR = "heartfelt_redemptor";
    /** 曾悔改标记(调试/成就用) */
    public static final String REDEMPTED = "heartfelt_redempted";
    /** 悔改完成时刻(游戏 tick)——记忆时间戳,与 REDEMPTED 一起写 */
    public static final String REDEMPTED_AT = "heartfelt_redempted_at";

    // ---------- v1.2.0:主动告白与关系破裂 ----------

    /** 主动告白被拒(正式失败)标记——置位后永不再主动告白 */
    public static final String CONFESSION_FAILED = "heartfelt_confession_failed";
    /** 主动告白被拒时刻(游戏 tick)——记忆时间戳,与 CONFESSION_FAILED 一起写 */
    public static final String CONFESSION_FAILED_AT = "heartfelt_confession_failed_at";
    /** 上次主动告白尝试的游戏 tick(冷却/去重) */
    public static final String LAST_CONFESSION_TICK = "heartfelt_last_confession_tick";
    /** 恋人关系破裂时刻(游戏 tick)——记忆"她心碎了" */
    public static final String HEARTBROKEN_AT = "heartfelt_heartbroken_at";

    // ---------- v1.2.2:女儿成长 ----------

    /** 女儿上次记录的成长阶段(heartfelt 自己跟踪,用于触发阶段升级事件) */
    public static final String CHILD_STAGE = "heartfelt_child_stage";
    /** 女儿上次成长阶段变化的时刻(游戏 tick)——记忆时间戳,阶段升级时写入 */
    public static final String CHILD_STAGE_AT = "heartfelt_child_stage_at";

    // ---------- v1.3.0:告白方向与事件历史 ----------

    /** 告白发起方("player"=玩家主动 / "maid"=女仆主动)——P0 方向修正后记录 */
    public static final String CONFESSION_BY = "heartfelt_confession_by";
    /** 告白完成时刻(游戏 tick)——与 CONFESSION_BY 一起写 */
    public static final String CONFESSION_AT = "heartfelt_confession_at";

    // ---------- v1.3.0:事件历史(P1) ----------

    /** 首次见面时刻(游戏 tick):heartfelt 首次观测到女仆属于该玩家 */
    public static final String EVENT_FIRST_MEET = "heartfelt_ev_first_meet";
    /** 首次送礼时刻(游戏 tick):首次喂食蛋糕等 */
    public static final String EVENT_FIRST_GIFT = "heartfelt_ev_first_gift";
    /** 救主次数(击杀攻击主人的敌对生物) */
    public static final String EVENT_SAVED_MASTER = "heartfelt_ev_saved_master";
    /** 最近一次救主时刻(游戏 tick) */
    public static final String EVENT_SAVED_AT = "heartfelt_ev_saved_at";
    /** 关系破裂累计次数(破裂史) */
    public static final String EVENT_BREAKUP_COUNT = "heartfelt_ev_breakup_count";
    /** 上次触发纪念日/回忆杀的游戏日(P5,防重复触发) */
    public static final String LAST_ANNIVERSARY_DAY = "heartfelt_ev_last_anniversary_day";

    // ---------- v1.3.1:特殊奶(maidmarriage special_milk_bucket) ----------

    /** 玩家最近一次喝下特殊奶的游戏 tick(记录"主人喝了我为他准备的奶"记忆) */
    public static final String SPECIAL_MILK_AT = "heartfelt_special_milk_at";
    /** 喝特殊奶的次数(累计,记忆/成就用) */
    public static final String SPECIAL_MILK_COUNT = "heartfelt_special_milk_count";

    // ---------- v1.4.0:孩子监护(魂符收妈妈修正) ----------

    /** 女儿在等妈妈回来(妈妈被魂符收走/死亡)——置位时强制坐下、禁用工作/移动 */
    public static final String WAITING_MOTHER = "heartfelt_waiting_mother";

    // ---------- v1.4.1:玩家伤害惩罚(关系不破坏,但有后果) ----------

    /** 伤心窗口截止游戏 tick(玩家连续伤害确认后置位;窗口内赌气/委屈) */
    public static final String HURT_UNTIL = "heartfelt_hurt_until";
    /** 每日惩罚次数记录日(游戏日) */
    public static final String HURT_PENALTY_DAY = "heartfelt_hurt_penalty_day";
    /** 当日已惩罚次数 */
    public static final String HURT_PENALTY_COUNT = "heartfelt_hurt_penalty_count";

    // ---------- v1.5.346:思慕明显效果 ----------

    /** 上次给玩家发思慕系统消息的游戏日(EntityMaid ForgeData,每日一次) */
    public static final String LAST_LONGING_MSG_DAY = "heartfelt_longing_msg_day";

    // ---------- v1.5.96:强制拾取/静音的原值落 NBT(跨实体重建保留) ----------

    /** 幼儿/婴儿被强制 PickType.ONLY_XP 前的【原拾取类型】(String,收魂符跟实体走) */
    public static final String PICKUP_ORIG = "heartfelt_pickup_orig";
    /** 幼儿/婴儿/伤心窗口被静音前的【原声音频率】(Float) */
    public static final String SOUND_ORIG = "heartfelt_sound_orig";

    // ---------- 与 Promaid 共享 ----------

    /** 自保逃跑标记(与 Promaid SelfPreservationBehavior 共享,值必须一致) */
    public static final String SELF_PRESERVING = "maid_smart_preserving";

    // ---------- v1.5.18:确认关系全局标记(玩家 ForgeData) ----------

    /**
     * 主人玩家持久标记:任一女仆确认过关系(结婚/告白)即置位、永不清除。
     * 用于吃醋隔离的【绝对压制】——不再依赖配偶是否在场(被收进魂符/暂时
     * 不在时旧版扫描落空,吃醋恢复)。
     */
    public static final String HEARTFELT_DEDICATED = "heartfelt_dedicated";
}
