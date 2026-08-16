package com.heartfelt.connection.affect;

/**
 * 关系阶段(v1.5.98,移植 MaidSoulCore RelationshipStage)——由事件脉冲 + 连续
 * 情感推导的【情感关系阶段】,区别于 maidmarriage 的"确认关系"(妻子/恋人/女儿)。
 * 它是"她此刻怎么看待这段关系"的动态档位,供 LLM prompt 注入语气。
 */
public enum RelationshipStage {
    COURTING("courting", "初识试探"),
    SWEET("sweet", "亲近甜蜜"),
    PASSIONATE("passionate", "热烈依恋"),
    STABLE("stable", "稳定陪伴"),
    COLD("cold", "冷淡防备"),
    REPAIRING("repairing", "修复中");

    private final String id;
    private final String zhName;

    RelationshipStage(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return this.id;
    }

    public String zhName() {
        return this.zhName;
    }

    public static RelationshipStage fromId(String id) {
        if (id == null || id.isBlank()) {
            return COURTING;
        }
        for (RelationshipStage stage : values()) {
            if (stage.id.equalsIgnoreCase(id.trim()) || stage.name().equalsIgnoreCase(id.trim())) {
                return stage;
            }
        }
        return COURTING;
    }
}
