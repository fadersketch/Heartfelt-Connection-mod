package com.heartfelt.connection.compat;

import java.lang.reflect.Method;

/**
 * Promaid 兼容适配层(v1.1.0):全局 API 配额桥迁入点。
 *
 * 与 Promaid 零硬依赖:装了 Promaid → 共享全局 LLM 配额(两模组主动对话合并计数);
 * 未装 → 反射失败、放行(无配额限制)。
 */
public final class PromaidCompat {
    private PromaidCompat() {
    }

    /** 配额内返回 true;Promaid 未装(反射失败)时放行返回 true */
    public static boolean tryAcquire() {
        Method m = ReflectUtil.staticMethod("com.maidsmart.dialogue.ApiQuotaManager", "tryAcquire");
        Object result = ReflectUtil.invokeStatic(m);
        // 反射失败(null)→ 放行;成功 → 取布尔结果
        return result == null || Boolean.TRUE.equals(result);
    }
}
