package com.campus.model;

public enum CouponType {
    /** 无门槛立减 */
    FIXED,
    /** 满减 */
    THRESHOLD,
    /** 折扣（discountPercent 为折扣系数，如 0.90=九折） */
    PERCENT
}
