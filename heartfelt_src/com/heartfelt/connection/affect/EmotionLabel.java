package com.heartfelt.connection.affect;

/**
 * 主导情绪标签(v1.5.98,移植 MaidSoulCore EmotionLabel)——由 VAD+亲密
 * 推导当前主导情绪,注入 LLM prompt 让对话语气贴合。
 */
public enum EmotionLabel {
    JOY("joy", "开心"),
    TRUST("trust", "信任"),
    CONTENTMENT("contentment", "安心"),
    EXCITEMENT("excitement", "兴奋期待"),
    LOVE("love", "依恋喜欢"),
    ANTICIPATION("anticipation", "期待"),
    ANXIETY("anxiety", "不安"),
    SADNESS("sadness", "难过"),
    ANGER("anger", "生气"),
    FEAR("fear", "害怕"),
    NEUTRAL("neutral", "平静");

    private final String id;
    private final String zhName;

    EmotionLabel(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return this.id;
    }

    public String zhName() {
        return this.zhName;
    }

    public static EmotionLabel fromId(String id) {
        if (id == null || id.isBlank()) {
            return NEUTRAL;
        }
        for (EmotionLabel label : values()) {
            if (label.id.equalsIgnoreCase(id.trim())) {
                return label;
            }
        }
        return NEUTRAL;
    }

    /** 由 VAD + 亲密推导主导情绪(与 MaidSoulCore 阈值一致) */
    public static EmotionLabel fromVad(double valence, double arousal, double dominance, double intimacy) {
        if (valence > 0.62 && intimacy > 0.62 && dominance < 0.58) {
            return LOVE;
        }
        if (valence > 0.5) {
            if (arousal > 0.62) {
                return dominance > 0.58 ? JOY : EXCITEMENT;
            }
            if (arousal < 0.38) {
                return CONTENTMENT;
            }
            return TRUST;
        }
        if (valence < -0.5) {
            if (arousal > 0.62) {
                if (dominance > 0.58) {
                    return ANGER;
                }
                if (dominance < 0.4) {
                    return FEAR;
                }
                return ANXIETY;
            }
            if (arousal < 0.38) {
                return SADNESS;
            }
            return ANXIETY;
        }
        if (arousal > 0.62) {
            return ANTICIPATION;
        }
        return NEUTRAL;
    }
}
