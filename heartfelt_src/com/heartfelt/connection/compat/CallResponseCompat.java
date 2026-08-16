package com.heartfelt.connection.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * callresponse(爱憎分明)兼容适配层(v1.1.0 全面重构)。
 *
 * 包名修正(A0):真实包名是 com.github.JumDa5he.callresponse
 * (1.0.0 误用 com.github.tartaricacid.callresponse → 全部静默失效)。
 * 所有调用经 ReflectUtil 安全反射,未装/版本不匹配时优雅降级。
 */
public final class CallResponseCompat {
    public static final String PACKAGE = "com.github.JumDa5he.callresponse.compat";

    private CallResponseCompat() {
    }

    // ---------- 情绪(emotion) ----------

    /** 背叛女仆判定(EmotionBetrayalManager.isBetraying) */
    public static boolean isBetraying(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(PACKAGE + ".emotion.EmotionBetrayalManager",
                "isBetraying", EntityMaid.class);
        return Boolean.TRUE.equals(ReflectUtil.invokeStatic(m, maid));
    }

    /** 解除背叛状态(原版遗留的公开 API,悔改系统使用) */
    public static boolean resetBetrayal(EntityMaid maid) {
        Method m = ReflectUtil.staticMethod(PACKAGE + ".emotion.EmotionBetrayalManager",
                "resetBetrayal", EntityMaid.class);
        return ReflectUtil.invokeStatic(m, maid) != null;
    }

    /** 恐惧增量(正为增加、负为安抚) */
    public static void addFearFloat(EntityMaid maid, UUID playerId, float delta) {
        Method m = ReflectUtil.staticMethod(PACKAGE + ".emotion.EmotionData",
                "addFearFloat", EntityMaid.class, UUID.class, float.class);
        ReflectUtil.invokeStatic(m, maid, playerId, delta);
    }

    /** 信任增量 */
    public static void addTrustFloat(EntityMaid maid, UUID playerId, float delta) {
        Method m = ReflectUtil.staticMethod(PACKAGE + ".emotion.EmotionData",
                "addTrustFloat", EntityMaid.class, UUID.class, float.class);
        ReflectUtil.invokeStatic(m, maid, playerId, delta);
    }

    /**
     * 读取信任/恐惧值(EmotionData.get → EmotionValues record)。
     *
     * @return [trust, fear];读取失败返回 null
     */
    public static int[] emotionValues(EntityMaid maid, UUID playerId) {
        Method get = ReflectUtil.staticMethod(PACKAGE + ".emotion.EmotionData",
                "get", EntityMaid.class, UUID.class);
        Object values = ReflectUtil.invokeStatic(get, maid, playerId);
        if (values == null) {
            return null;
        }
        Method trust = ReflectUtil.method(values.getClass(), "trust");
        Method fear = ReflectUtil.method(values.getClass(), "fear");
        Object t = ReflectUtil.invoke(trust, values);
        Object f = ReflectUtil.invoke(fear, values);
        if (t instanceof Number tn && f instanceof Number fn) {
            return new int[]{tn.intValue(), fn.intValue()};
        }
        return null;
    }

    /** 溺爱动作枚举值 FLOWER(EmotionDotingManager$ActionType.FLOWER);失败返回 null */
    public static Object actionTypeFlower() {
        Class<?> cls = ReflectUtil.load(PACKAGE + ".emotion.EmotionDotingManager$ActionType");
        if (cls == null || !cls.isEnum()) {
            return null;
        }
        for (Object constant : cls.getEnumConstants()) {
            if ("FLOWER".equals(((Enum<?>) constant).name())) {
                return constant;
            }
        }
        return null;
    }

    // ---------- 饥饿(hunger) ----------

    /** 读取饥饿值(缺省 50,失败不产生跳变) */
    public static float hungerOf(EntityMaid maid) {
        Method get = ReflectUtil.staticMethod(PACKAGE + ".hunger.HungerData",
                "get", EntityMaid.class);
        Object value = ReflectUtil.invokeStatic(get, maid);
        return value instanceof Number n ? n.floatValue() : 50.0f;
    }

    /** 写饥饿值(自带客户端同步);失败静默 */
    public static void hungerSet(EntityMaid maid, float value) {
        Method set = ReflectUtil.staticMethod(PACKAGE + ".hunger.HungerData",
                "set", EntityMaid.class, float.class);
        ReflectUtil.invokeStatic(set, maid, value);
    }
}
