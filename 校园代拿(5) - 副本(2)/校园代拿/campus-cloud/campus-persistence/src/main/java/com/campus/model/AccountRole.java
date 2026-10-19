package com.campus.model;

/**
 * 平台账号角色（与接单员资格 {@code riderApproved} 正交）。
 * 管理员 / 人工客服互斥，普通用户为 {@link #USER}。
 */
public enum AccountRole {
    USER,
    ADMIN,
    CUSTOMER_SERVICE;

    public static AccountRole fromFlags(boolean platformAdmin, boolean customerServiceStaff) {
        if (platformAdmin) {
            return ADMIN;
        }
        if (customerServiceStaff) {
            return CUSTOMER_SERVICE;
        }
        return USER;
    }

    public static AccountRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String key = raw.trim().toUpperCase().replace('-', '_');
        return switch (key) {
            case "USER", "NORMAL" -> USER;
            case "ADMIN", "PLATFORM_ADMIN" -> ADMIN;
            case "CUSTOMER_SERVICE", "CS", "CS_STAFF", "CUSTOMER_SERVICE_STAFF" -> CUSTOMER_SERVICE;
            default -> throw new IllegalArgumentException("不支持的角色：" + raw);
        };
    }
}
