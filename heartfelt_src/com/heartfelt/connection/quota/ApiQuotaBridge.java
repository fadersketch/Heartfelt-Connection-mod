package com.heartfelt.connection.quota;

import com.heartfelt.connection.compat.PromaidCompat;

/**
 * 全局 API 配额桥(v1.1.0 重构):保留类名做门面,实现迁入 compat/PromaidCompat。
 * 调用方无需改动。
 */
public final class ApiQuotaBridge {
    private ApiQuotaBridge() {
    }

    /** 配额内返回 true;Promaid 未装(反射失败)时放行返回 true */
    public static boolean tryAcquire() {
        return PromaidCompat.tryAcquire();
    }
}
