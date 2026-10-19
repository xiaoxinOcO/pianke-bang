package com.campus.support;

import java.math.BigDecimal;

public record CouponQuote(
        BigDecimal reward,
        BigDecimal discount,
        BigDecimal payable,
        String reason,
        boolean applicable
) {
    public static CouponQuote none(BigDecimal reward) {
        BigDecimal r = reward == null ? BigDecimal.ZERO : reward;
        return new CouponQuote(r, BigDecimal.ZERO, r, "", true);
    }
}
