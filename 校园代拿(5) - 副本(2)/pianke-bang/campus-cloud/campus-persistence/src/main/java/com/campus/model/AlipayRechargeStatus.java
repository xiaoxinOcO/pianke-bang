package com.campus.model;

public enum AlipayRechargeStatus {
    PENDING,
    PAID,
    CLOSED,
    /** 已向支付宝买家原路退款 */
    REFUNDED
}
