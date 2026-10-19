package com.campus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminUserDTO {
    private Long id;
    private String username;
    /** 短信注册时写入的号码，可与接单员申请手机号不同 */
    private String phone;
    private boolean enabled;
    private boolean avatarPending;
    private String avatarUrl;
    private String pendingAvatarUrl;
    private boolean riderApproved;
    private boolean riderApplicationPending;
    private Integer creditScore;
    private LocalDateTime blockedUntil;
    private BigDecimal pendingPenalty;
    private String riderRealName;
    private String riderStudentId;
    private String riderPhone;
    private String riderApplyReason;
    private LocalDateTime riderAppliedAt;
    private boolean online;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private boolean customerServiceStaff;
    private boolean platformAdmin;
    /** USER / ADMIN / CUSTOMER_SERVICE */
    private String accountRole;

    public AdminUserDTO(
            Long id,
            String username,
            String phone,
            boolean enabled,
            boolean avatarPending,
            String avatarUrl,
            String pendingAvatarUrl,
            boolean riderApproved,
            boolean riderApplicationPending,
            Integer creditScore,
            LocalDateTime blockedUntil,
            java.math.BigDecimal pendingPenalty,
            String riderRealName,
            String riderStudentId,
            String riderPhone,
            String riderApplyReason,
            LocalDateTime riderAppliedAt,
            boolean online,
            LocalDateTime lastSeenAt,
            LocalDateTime createdAt,
            boolean customerServiceStaff,
            boolean platformAdmin,
            String accountRole
    ) {
        this.id = id;
        this.username = username;
        this.phone = phone;
        this.enabled = enabled;
        this.avatarPending = avatarPending;
        this.avatarUrl = avatarUrl;
        this.pendingAvatarUrl = pendingAvatarUrl;
        this.riderApproved = riderApproved;
        this.riderApplicationPending = riderApplicationPending;
        this.creditScore = creditScore;
        this.blockedUntil = blockedUntil;
        this.pendingPenalty = pendingPenalty;
        this.riderRealName = riderRealName;
        this.riderStudentId = riderStudentId;
        this.riderPhone = riderPhone;
        this.riderApplyReason = riderApplyReason;
        this.riderAppliedAt = riderAppliedAt;
        this.online = online;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
        this.customerServiceStaff = customerServiceStaff;
        this.platformAdmin = platformAdmin;
        this.accountRole = accountRole;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvatarPending() {
        return avatarPending;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public java.math.BigDecimal getPendingPenalty() {
        return pendingPenalty;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPendingAvatarUrl() {
        return pendingAvatarUrl;
    }

    public boolean isRiderApproved() {
        return riderApproved;
    }

    public boolean isRiderApplicationPending() {
        return riderApplicationPending;
    }

    public String getRiderRealName() {
        return riderRealName;
    }

    public String getRiderStudentId() {
        return riderStudentId;
    }

    public String getRiderPhone() {
        return riderPhone;
    }

    public String getRiderApplyReason() {
        return riderApplyReason;
    }

    public LocalDateTime getRiderAppliedAt() {
        return riderAppliedAt;
    }

    public boolean isOnline() {
        return online;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCustomerServiceStaff() {
        return customerServiceStaff;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public String getAccountRole() {
        return accountRole;
    }
}
