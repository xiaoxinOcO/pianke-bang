package com.campus.mq;

public final class CampusMqConstants {

    public static final String EXCHANGE = "campus.direct";

    public static final String RK_TASK_STATUS = "task.status";
    public static final String RK_AUDIT_LOG = "audit.log";
    public static final String RK_WALLET_LOCK = "wallet.lock";
    public static final String RK_WALLET_REFUND = "wallet.refund";
    public static final String RK_WALLET_INCOME = "wallet.income";
    public static final String RK_WALLET_COMPENSATE = "wallet.compensate";

    public static final String QUEUE_CHAT_PUSH = "campus.chat.push";
    public static final String QUEUE_AUDIT_EVENT = "campus.audit.event";
    public static final String QUEUE_WALLET_LOCK = "campus.wallet.lock";
    public static final String QUEUE_WALLET_REFUND = "campus.wallet.refund";
    public static final String QUEUE_WALLET_INCOME = "campus.wallet.income";
    public static final String QUEUE_WALLET_COMPENSATE = "campus.wallet.compensate";

    private CampusMqConstants() {
    }
}
