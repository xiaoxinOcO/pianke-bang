package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_redeem_log")
public class CouponRedeemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_id", nullable = false)
    private Long codeId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @CreationTimestamp
    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private LocalDateTime redeemedAt;

    public Long getId() { return id; }

    public Long getCodeId() { return codeId; }
    public void setCodeId(Long codeId) { this.codeId = codeId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }

    public LocalDateTime getRedeemedAt() { return redeemedAt; }
}
