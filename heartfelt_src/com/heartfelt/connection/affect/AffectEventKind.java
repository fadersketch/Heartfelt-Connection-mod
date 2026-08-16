package com.heartfelt.connection.affect;

/**
 * 情感事件类型(v1.5.98,移植 MaidSoulCore AffectEventKind)。
 * heartfelt 从现有事件点喂脉冲:被打/喂食/亲密/告白/破裂/哀悼/思慕等。
 */
public enum AffectEventKind {
    /** 玩家喂食蛋糕/送礼/喂奶 → 正向亲和 */
    OWNER_AFFECTION("owner_affection"),
    /** 悔改安抚(喂食中安抚背叛女仆)→ 修复关系 */
    OWNER_APOLOGY("owner_apology"),
    /** 玩家攻击女仆(近战/远程)→ 冲突 + 受伤债 */
    MAID_HURT_BY_OWNER("maid_hurt_by_owner"),
    /** 环境/怪物伤害 → 轻度受伤债 */
    MAID_HURT_BY_WORLD("maid_hurt_by_world"),
    /** 主人死亡/哀悼 → 悲伤 + 思慕 */
    OWNER_DISTRESS("owner_distress"),
    /** 主人长时间不在(思慕检测)→ 思念上涨 */
    OWNER_LONG_ABSENCE("owner_long_absence"),
    /** 告白成功 → 亲密大涨 */
    CONFESSION_SUCCESS("confession_success"),
    /** 告白被拒 → 低落 + 修复债 */
    CONFESSION_FAILED("confession_failed"),
    /** 关系破裂 → 冲突 + 受伤债 */
    HEARTBROKEN("heartbroken"),
    /** 亲密互动(拥抱/亲吻/膝枕/摸头)→ 正向 */
    INTIMATE_INTERACTION("intimate_interaction"),
    /** 纪念日 → 正向 */
    HOLIDAY_CELEBRATION("holiday_celebration"),
    /** 安静时间流逝(周期衰减)→ 向基线回退 */
    QUIET_RECOVERY("quiet_recovery");

    private final String id;

    AffectEventKind(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
