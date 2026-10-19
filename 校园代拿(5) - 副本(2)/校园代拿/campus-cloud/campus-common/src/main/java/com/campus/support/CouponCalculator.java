package com.campus.support;

import com.campus.model.CouponTemplate;
import com.campus.model.CouponType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponCalculator {

    private static final BigDecimal MIN_PAYABLE = new BigDecimal("0.01");

    private CouponCalculator() {
    }

    public static CouponQuote quote(CouponTemplate template, BigDecimal reward) {
        if (reward == null || reward.signum() <= 0) {
            return new CouponQuote(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "赏金无效", false);
        }
        BigDecimal r = reward.setScale(2, RoundingMode.HALF_UP);
        if (template == null || template.getType() == null) {
            return CouponQuote.none(r);
        }

        BigDecimal maxCut = r.subtract(MIN_PAYABLE).max(BigDecimal.ZERO);
        BigDecimal discount = BigDecimal.ZERO;
        String reason = "";

        CouponType type = template.getType();
        if (type == CouponType.FIXED) {
            BigDecimal amt = nullToZero(template.getDiscountAmount());
            discount = amt.min(maxCut);
            reason = "立减 ¥" + discount.toPlainString();
        } else if (type == CouponType.THRESHOLD) {
            BigDecimal minSpend = nullToZero(template.getMinSpend());
            if (r.compareTo(minSpend) < 0) {
                return new CouponQuote(r, BigDecimal.ZERO, r,
                        "未满 ¥" + minSpend.toPlainString() + " 门槛", false);
            }
            BigDecimal amt = nullToZero(template.getDiscountAmount());
            discount = amt.min(maxCut);
            reason = "满" + minSpend.toPlainString() + "减" + discount.toPlainString();
        } else if (type == CouponType.PERCENT) {
            BigDecimal percent = template.getDiscountPercent();
            if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.ONE) >= 0) {
                return new CouponQuote(r, BigDecimal.ZERO, r, "折扣配置无效", false);
            }
            // percent=0.90 -> 九折，抵扣 10%
            BigDecimal cutRate = BigDecimal.ONE.subtract(percent);
            discount = r.multiply(cutRate).setScale(2, RoundingMode.HALF_UP);
            if (template.getMaxDiscount() != null && template.getMaxDiscount().signum() > 0) {
                discount = discount.min(template.getMaxDiscount());
            }
            discount = discount.min(maxCut);
            reason = "享" + percent.multiply(new BigDecimal("10")).stripTrailingZeros().toPlainString() + "折";
        }

        if (discount.signum() <= 0) {
            return new CouponQuote(r, BigDecimal.ZERO, r, "本单不可抵扣", false);
        }
        BigDecimal payable = r.subtract(discount).max(MIN_PAYABLE).setScale(2, RoundingMode.HALF_UP);
        discount = r.subtract(payable).setScale(2, RoundingMode.HALF_UP);
        return new CouponQuote(r, discount, payable, reason, true);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
