package com.heartfelt.connection.client;

/**
 * 客户端初始化(v1.2.0)——经 DistExecutor 在 CLIENT 侧调用。
 * 当前无额外客户端注册;预留扩展点(如需要客户端事件监听时在此挂载)。
 */
public final class ClientInit {
    private ClientInit() {
    }

    public static void init() {
        // 预留:客户端侧初始化
    }
}
