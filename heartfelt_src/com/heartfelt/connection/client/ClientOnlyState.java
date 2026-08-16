package com.heartfelt.connection.client;

/**
 * 客户端运行状态助手(v1.5.366)——对话帧拦截等客户端逻辑取"当前交互女仆"的收敛点。
 * 心契誓约的交互状态分两套:成人 HugClientState / 儿童 ChildInteractionClientState,
 * 两套都查,返回当前交互女仆 UUID(无则 null)。反射软联动,类不存在静默。
 */
public final class ClientOnlyState {
    private ClientOnlyState() {
    }

    /** 当前交互女仆 UUID(成人 HugClientState → 儿童 ChildInteractionClientState) */
    public static java.util.UUID interactionMaidUuid() {
        try {
            Class<?> cls = Class.forName("com.example.maidmarriage.client.HugClientState");
            Object value = cls.getMethod("getLocalInteractionMaidUuid").invoke(null);
            if (value instanceof java.util.UUID uuid) {
                return uuid;
            }
        } catch (Exception ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.example.maidmarriage.client.ChildInteractionClientState");
            Object value = cls.getMethod("getLocalInteractionMaidUuid").invoke(null);
            if (value instanceof java.util.UUID uuid) {
                return uuid;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
