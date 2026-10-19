package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupon")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status = UserCouponStatus.UNUSED;

    @CreationTimestamp
    @Column(name = "obtained_at", nullable = false, updatable = false)
    private LocalDateTime obtainedAt;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_task_id")
    private Long usedTaskId;

    public Long getId() { return id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UserCouponStatus getStatus() { return status; }
    public void setStatus(UserCouponStatus status) { this.status = status; }

    public LocalDateTime getObtainedAt() { return obtainedAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public Long getUsedTaskId() { return usedTaskId; }
    public void setUsedTaskId(Long usedTaskId) { this.usedTaskId = usedTaskId; }
}
