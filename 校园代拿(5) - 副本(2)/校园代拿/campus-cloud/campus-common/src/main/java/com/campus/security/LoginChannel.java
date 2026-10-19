package com.campus.security;

/**
 * 登录端：网页 / 接单员台 / 小程序会话相互独立，互不踢下线；
 * 同一端内仍保持单点登录。
 */
public final class LoginChannel {

    public static final String WEB = "web";
    public static final String MP = "mp";
    /** 接单员工作台（与主站网页会话独立） */
    public static final String RIDER = "rider";

    private LoginChannel() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        String c = raw.trim().toLowerCase();
        if (MP.equals(c)) {
            return MP;
        }
        if (RIDER.equals(c)) {
            return RIDER;
        }
        return WEB;
    }

    public static boolean isMp(String channel) {
        return MP.equals(normalize(channel));
    }

    public static boolean isRider(String channel) {
        return RIDER.equals(normalize(channel));
    }
}
