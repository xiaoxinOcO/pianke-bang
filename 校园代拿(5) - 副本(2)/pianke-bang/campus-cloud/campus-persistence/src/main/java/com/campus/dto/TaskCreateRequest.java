package com.campus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TaskCreateRequest {
    private String taskType;
    private String title;
    private String pickupLocation;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String dropoffLocation;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private BigDecimal reward;
    private String publisherName;
    private String contact;
    private String description;
    private LocalDateTime deliveryStartAt;
    private LocalDateTime deliveryEndAt;
    private LocalDateTime latestDeliveryAt;
    private List<String> imageUrls;
    /** 赏金支付方式：ALIPAY / WECHAT / WALLET */
    private String paymentMethod;
    /** 使用的用户优惠券 id（可选） */
    private Long userCouponId;
    /** 是否加急 */
    private Boolean urgentEnabled;
    /** 加急费（元），开启时必填 */
    private BigDecimal urgentFee;

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
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

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDropoffLocation() {
        return dropoffLocation;
    }

    public void setDropoffLocation(String dropoffLocation) {
        this.dropoffLocation = dropoffLocation;
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

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
}
