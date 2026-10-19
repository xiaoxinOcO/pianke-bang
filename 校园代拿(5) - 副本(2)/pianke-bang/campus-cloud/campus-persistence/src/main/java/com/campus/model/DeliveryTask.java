package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_task")
public class DeliveryTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 任务分类：EXPRESS / FOOD / PRINT / QUEUE / OTHER */
    @Column(nullable = false, length = 32)
    private String taskType = "EXPRESS";

    @Column(nullable = false, length = 120)
    private String pickupLocation;

    @Column(precision = 10, scale = 6)
    private BigDecimal pickupLat;

    @Column(precision = 10, scale = 6)
    private BigDecimal pickupLng;

    @Column(nullable = false, length = 120)
    private String dropoffLocation;

    @Column(precision = 10, scale = 6)
    private BigDecimal dropoffLat;

    @Column(precision = 10, scale = 6)
    private BigDecimal dropoffLng;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal reward;

    @Column(nullable = false, length = 50)
    private String publisherName;

    @Column(nullable = false, length = 100)
    private String contact;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private LocalDateTime deliveryStartAt;

    @Column
    private LocalDateTime deliveryEndAt;

    @Column
    private LocalDateTime latestDeliveryAt;

    /** 图片列表（JSON 数组字符串） */
    @Column(columnDefinition = "LONGTEXT")
    private String imageUrls;

    /** 接单员提交完成时的留言 */
    @Column(name = "completion_note", length = 500)
    private String completionNote;

    /** 完成凭证图片（JSON 数组字符串，可为 dataURL） */
    @Column(name = "completion_proof_images", columnDefinition = "LONGTEXT")
    private String completionProofImages;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(length = 50)
    private String claimerName;

    @Column(precision = 2, scale = 1)
    private java.math.BigDecimal claimerRating;

    @Column(length = 300)
    private String claimerRatingComment;

    @Column
    private LocalDateTime claimerRatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 申诉裁决：对接单员“不扣完成率”的标记。
     * - true: 该任务对接单员统计不计入失败（如被取消/驳回但申诉成立）
     * - null/false: 正常统计口径
     */
    @Column
    private Boolean riderNoFault;

    @Column
    private Boolean appealApproved;

    @Column
    private Boolean appealServiceCompleted;

    /** 超时驳回标记：用于前端把“接单员超时”单独展示到驳回任务里 */
    @Column(nullable = false)
    private Boolean overdueRejected = false;

    @Column(length = 50)
    private String appealAuditedBy;

    @Column(length = 300)
    private String appealRemark;

    @Column
    private LocalDateTime appealAuditedAt;

    /**
     * 是否已从发布者钱包扣除赏金（待结算给接单员或退还）。
     * null/false：历史任务或未走预付流程。
     */
    @Column
    private Boolean bountyPrepaid;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "coupon_discount", precision = 12, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "bounty_payable", precision = 12, scale = 2)
    private BigDecimal bountyPayable;

    /** 是否加急 */
    @Column(name = "urgent_enabled", nullable = false)
    private Boolean urgentEnabled = false;

    /** 用户支付的加急费总额 */
    @Column(name = "urgent_fee", precision = 12, scale = 2)
    private BigDecimal urgentFee;

    /** 接单员加急赏（约 80%） */
    @Column(name = "urgent_rider_bonus", precision = 12, scale = 2)
    private BigDecimal urgentRiderBonus;

    /** 平台推送费（约 20%） */
    @Column(name = "urgent_push_fee", precision = 12, scale = 2)
    private BigDecimal urgentPushFee;

    /** 加急生效时间（支付成功后写入，用于 30 分钟加权） */
    @Column(name = "urgent_at")
    private LocalDateTime urgentAt;

    /** 管理员驳回原因（用于展示给发布者）；当任务重新提交/通过时会被清空 */
    @Column(length = 500)
    private String adminRejectRemark;

    public DeliveryTask() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public BigDecimal getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(BigDecimal pickupLat) {
        this.pickupLat = pickupLat;
    }

    public BigDecimal getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(BigDecimal pickupLng) {
        this.pickupLng = pickupLng;
    }

    public String getDropoffLocation() {
        return dropoffLocation;
    }

    public void setDropoffLocation(String dropoffLocation) {
        this.dropoffLocation = dropoffLocation;
    }

    public BigDecimal getDropoffLat() {
        return dropoffLat;
    }

    public void setDropoffLat(BigDecimal dropoffLat) {
        this.dropoffLat = dropoffLat;
    }

    public BigDecimal getDropoffLng() {
        return dropoffLng;
    }

    public void setDropoffLng(BigDecimal dropoffLng) {
        this.dropoffLng = dropoffLng;
    }

    public BigDecimal getReward() {
        return reward;
    }

    public void setReward(BigDecimal reward) {
        this.reward = reward;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getLatestDeliveryAt() {
        return latestDeliveryAt;
    }

    public void setLatestDeliveryAt(LocalDateTime latestDeliveryAt) {
        this.latestDeliveryAt = latestDeliveryAt;
    }

    public LocalDateTime getDeliveryStartAt() {
        return deliveryStartAt;
    }

    public void setDeliveryStartAt(LocalDateTime deliveryStartAt) {
        this.deliveryStartAt = deliveryStartAt;
    }

    public LocalDateTime getDeliveryEndAt() {
        return deliveryEndAt;
    }

    public void setDeliveryEndAt(LocalDateTime deliveryEndAt) {
        this.deliveryEndAt = deliveryEndAt;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getCompletionNote() {
        return completionNote;
    }

    public void setCompletionNote(String completionNote) {
        this.completionNote = completionNote;
    }

    public String getCompletionProofImages() {
        return completionProofImages;
    }

    public void setCompletionProofImages(String completionProofImages) {
        this.completionProofImages = completionProofImages;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getClaimerName() {
        return claimerName;
    }

    public void setClaimerName(String claimerName) {
        this.claimerName = claimerName;
    }

    public java.math.BigDecimal getClaimerRating() {
        return claimerRating;
    }

    public void setClaimerRating(java.math.BigDecimal claimerRating) {
        this.claimerRating = claimerRating;
    }

    public String getClaimerRatingComment() {
        return claimerRatingComment;
    }

    public void setClaimerRatingComment(String claimerRatingComment) {
        this.claimerRatingComment = claimerRatingComment;
    }

    public LocalDateTime getClaimerRatedAt() {
        return claimerRatedAt;
    }

    public void setClaimerRatedAt(LocalDateTime claimerRatedAt) {
        this.claimerRatedAt = claimerRatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Boolean getRiderNoFault() {
        return riderNoFault;
    }

    public void setRiderNoFault(Boolean riderNoFault) {
        this.riderNoFault = riderNoFault;
    }

    public Boolean getAppealApproved() {
        return appealApproved;
    }

    public void setAppealApproved(Boolean appealApproved) {
        this.appealApproved = appealApproved;
    }

    public Boolean getAppealServiceCompleted() {
        return appealServiceCompleted;
    }

    public void setAppealServiceCompleted(Boolean appealServiceCompleted) {
        this.appealServiceCompleted = appealServiceCompleted;
    }

    public Boolean getOverdueRejected() {
        return overdueRejected;
    }

    public void setOverdueRejected(Boolean overdueRejected) {
        this.overdueRejected = overdueRejected;
    }

    public String getAppealAuditedBy() {
        return appealAuditedBy;
    }

    public void setAppealAuditedBy(String appealAuditedBy) {
        this.appealAuditedBy = appealAuditedBy;
    }

    public String getAppealRemark() {
        return appealRemark;
    }

    public void setAppealRemark(String appealRemark) {
        this.appealRemark = appealRemark;
    }

    public LocalDateTime getAppealAuditedAt() {
        return appealAuditedAt;
    }

    public void setAppealAuditedAt(LocalDateTime appealAuditedAt) {
        this.appealAuditedAt = appealAuditedAt;
    }

    public Boolean getBountyPrepaid() {
        return bountyPrepaid;
    }

    public void setBountyPrepaid(Boolean bountyPrepaid) {
        this.bountyPrepaid = bountyPrepaid;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(BigDecimal couponDiscount) {
        this.couponDiscount = couponDiscount;
    }

    public BigDecimal getBountyPayable() {
        return bountyPayable;
    }

    public void setBountyPayable(BigDecimal bountyPayable) {
        this.bountyPayable = bountyPayable;
    }

    public Boolean getUrgentEnabled() {
        return urgentEnabled;
    }

    public void setUrgentEnabled(Boolean urgentEnabled) {
        this.urgentEnabled = urgentEnabled;
    }

    public BigDecimal getUrgentFee() {
        return urgentFee;
    }

    public void setUrgentFee(BigDecimal urgentFee) {
        this.urgentFee = urgentFee;
    }

    public BigDecimal getUrgentRiderBonus() {
        return urgentRiderBonus;
    }

    public void setUrgentRiderBonus(BigDecimal urgentRiderBonus) {
        this.urgentRiderBonus = urgentRiderBonus;
    }

    public BigDecimal getUrgentPushFee() {
        return urgentPushFee;
    }

    public void setUrgentPushFee(BigDecimal urgentPushFee) {
        this.urgentPushFee = urgentPushFee;
    }

    public LocalDateTime getUrgentAt() {
        return urgentAt;
    }

    public void setUrgentAt(LocalDateTime urgentAt) {
        this.urgentAt = urgentAt;
    }

    public String getAdminRejectRemark() {
        return adminRejectRemark;
    }

    public void setAdminRejectRemark(String adminRejectRemark) {
        this.adminRejectRemark = adminRejectRemark;
    }
}
