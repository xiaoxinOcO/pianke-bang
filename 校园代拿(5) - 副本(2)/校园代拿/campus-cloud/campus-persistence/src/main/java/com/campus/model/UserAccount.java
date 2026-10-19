package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 网页端当前有效会话（同账号仅允许一个网页登录） */
    @Column(length = 64)
    private String activeSessionId;

    /** 小程序端当前有效会话（与网页会话独立，互不踢下线） */
    @Column(length = 64)
    private String activeMpSessionId;

    /** 接单员工作台当前有效会话（与主站网页会话独立，互不踢下线） */
    @Column(length = 64)
    private String activeRiderSessionId;

    @Column
    private LocalDateTime lastSeenAt;

    @Column(columnDefinition = "LONGTEXT")
    private String avatarUrl;

    @Column(columnDefinition = "LONGTEXT")
    private String pendingAvatarUrl;

    @Column(nullable = false)
    private boolean avatarPending = false;

    @Column(nullable = false)
    private boolean riderApproved = false;

    @Column(nullable = false)
    private boolean riderApplicationPending = false;

    @Column(length = 50)
    private String realName;

    @Column(length = 50)
    private String studentId;

    @Column(length = 10)
    private String gender;

    @Column(length = 30)
    private String phone;

    /** 微信开放平台 unionid（网站应用与小程序绑定同一开放平台账号时可互通） */
    @Column(length = 64, unique = true)
    private String wxUnionId;

    /** 微信小程序 openid */
    @Column(length = 64, unique = true)
    private String wxMpOpenId;

    /** 微信开放平台网站应用 openid（扫码登录） */
    @Column(length = 64, unique = true)
    private String wxWebOpenId;

    @Column(length = 50)
    private String riderRealName;

    @Column(length = 50)
    private String riderStudentId;

    @Column(length = 30)
    private String riderPhone;

    @Column(length = 300)
    private String riderApplyReason;

    @Column
    private LocalDateTime riderAppliedAt;

    @Column(nullable = false)
    private Integer creditScore = 80;

    @Column
    private LocalDateTime blockedUntil;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingPenalty = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean customerServiceStaff = false;

    /** 平台管理员（库内角色；与客服互斥） */
    @Column(nullable = false)
    private boolean platformAdmin = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public String getActiveMpSessionId() {
        return activeMpSessionId;
    }

    public void setActiveMpSessionId(String activeMpSessionId) {
        this.activeMpSessionId = activeMpSessionId;
    }

    public String getActiveRiderSessionId() {
        return activeRiderSessionId;
    }

    public void setActiveRiderSessionId(String activeRiderSessionId) {
        this.activeRiderSessionId = activeRiderSessionId;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPendingAvatarUrl() {
        return pendingAvatarUrl;
    }

    public void setPendingAvatarUrl(String pendingAvatarUrl) {
        this.pendingAvatarUrl = pendingAvatarUrl;
    }

    public boolean isAvatarPending() {
        return avatarPending;
    }

    public void setAvatarPending(boolean avatarPending) {
        this.avatarPending = avatarPending;
    }

    public boolean isRiderApproved() {
        return riderApproved;
    }

    public void setRiderApproved(boolean riderApproved) {
        this.riderApproved = riderApproved;
    }

    public boolean isRiderApplicationPending() {
        return riderApplicationPending;
    }

    public void setRiderApplicationPending(boolean riderApplicationPending) {
        this.riderApplicationPending = riderApplicationPending;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWxUnionId() {
        return wxUnionId;
    }

    public void setWxUnionId(String wxUnionId) {
        this.wxUnionId = wxUnionId;
    }

    public String getWxMpOpenId() {
        return wxMpOpenId;
    }

    public void setWxMpOpenId(String wxMpOpenId) {
        this.wxMpOpenId = wxMpOpenId;
    }

    public String getWxWebOpenId() {
        return wxWebOpenId;
    }

    public void setWxWebOpenId(String wxWebOpenId) {
        this.wxWebOpenId = wxWebOpenId;
    }

    public String getRiderRealName() {
        return riderRealName;
    }

    public void setRiderRealName(String riderRealName) {
        this.riderRealName = riderRealName;
    }

    public String getRiderStudentId() {
        return riderStudentId;
    }

    public void setRiderStudentId(String riderStudentId) {
        this.riderStudentId = riderStudentId;
    }

    public String getRiderPhone() {
        return riderPhone;
    }

    public void setRiderPhone(String riderPhone) {
        this.riderPhone = riderPhone;
    }

    public String getRiderApplyReason() {
        return riderApplyReason;
    }

    public void setRiderApplyReason(String riderApplyReason) {
        this.riderApplyReason = riderApplyReason;
    }

    public LocalDateTime getRiderAppliedAt() {
        return riderAppliedAt;
    }

    public void setRiderAppliedAt(LocalDateTime riderAppliedAt) {
        this.riderAppliedAt = riderAppliedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public BigDecimal getPendingPenalty() {
        return pendingPenalty;
    }

    public void setPendingPenalty(BigDecimal pendingPenalty) {
        this.pendingPenalty = pendingPenalty;
    }

    public boolean isCustomerServiceStaff() {
        return customerServiceStaff;
    }

    public void setCustomerServiceStaff(boolean customerServiceStaff) {
        this.customerServiceStaff = customerServiceStaff;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public void setPlatformAdmin(boolean platformAdmin) {
        this.platformAdmin = platformAdmin;
    }

    /** 由管理员 / 客服标志推导的平台角色 */
    public AccountRole resolveAccountRole() {
        return AccountRole.fromFlags(platformAdmin, customerServiceStaff);
    }

    /**
     * 写入平台角色并保证管理员与客服互斥。
     */
    public void applyAccountRole(AccountRole role) {
        AccountRole target = role == null ? AccountRole.USER : role;
        switch (target) {
            case ADMIN -> {
                this.platformAdmin = true;
                this.customerServiceStaff = false;
            }
            case CUSTOMER_SERVICE -> {
                this.platformAdmin = false;
                this.customerServiceStaff = true;
                if (this.avatarUrl == null || this.avatarUrl.isBlank()) {
                    this.avatarUrl = CUSTOMER_SERVICE_AVATAR_URL;
                }
            }
            case USER -> {
                this.platformAdmin = false;
                this.customerServiceStaff = false;
            }
        }
    }

    public static final String CUSTOMER_SERVICE_AVATAR_URL = "/logo.png";

    /** 人工客服统一使用平台 logo 作为对外展示头像 */
    public String resolveDisplayAvatarUrl() {
        if (customerServiceStaff) {
            return CUSTOMER_SERVICE_AVATAR_URL;
        }
        return avatarUrl == null ? "" : avatarUrl;
    }
}
