package com.campus.config;

public final class ServiceConstants {
    public static final String HEADER_USERNAME = "X-Campus-Username";
    public static final String HEADER_USERNAME_ENCODING = "X-Campus-Username-Enc";
    public static final String HEADER_USER_ID = "X-Campus-User-Id";
    public static final String HEADER_SESSION_ID = "X-Campus-Session-Id";
    /** 服务间 / 网关转发凭证，禁止客户端伪造后被信任 */
    public static final String HEADER_INTERNAL_TOKEN = "X-Campus-Internal-Token";

    public static final String AUTH_SERVICE = "campus-auth-service";
    public static final String USER_SERVICE = "campus-user-service";
    public static final String TASK_SERVICE = "campus-task-service";
    public static final String AUDIT_SERVICE = "campus-audit-service";
    public static final String CHAT_SERVICE = "campus-chat-service";
    public static final String WALLET_SERVICE = "campus-wallet-service";

    private ServiceConstants() {
    }
}
