package com.campus.dto;

import java.math.BigDecimal;

public class CouponPreviewRequest {
    private BigDecimal reward;
    private Long userCouponId;

    public BigDecimal getReward() { return reward; }
    public void setReward(BigDecimal reward) { this.reward = reward; }
    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
}
