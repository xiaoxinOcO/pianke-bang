package com.campus.dto;

public class HallActivityUpsertRequest {
    private String title;
    private String subtitle;
    private String bannerImageUrl;
    /** 网页端海报 URL（base64 或外链） */
    private String bannerImageUrlWeb;
    private String bannerTheme;
    private String ctaText;
    private String actionType;
    private String actionValue;
    private Long couponTemplateId;
    private Integer sortOrder;
    private String status;
    /** 领券活动：若未指定 couponTemplateId，可附带金额自动建公开券 */
    private Double couponAmount;
    /** 自动建券有效天数；null/≤0 表示不限期限 */
    private Integer couponValidDays;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getBannerImageUrl() { return bannerImageUrl; }
    public void setBannerImageUrl(String bannerImageUrl) { this.bannerImageUrl = bannerImageUrl; }
    public String getBannerImageUrlWeb() { return bannerImageUrlWeb; }
    public void setBannerImageUrlWeb(String bannerImageUrlWeb) { this.bannerImageUrlWeb = bannerImageUrlWeb; }
    public String getBannerTheme() { return bannerTheme; }
    public void setBannerTheme(String bannerTheme) { this.bannerTheme = bannerTheme; }
    public String getCtaText() { return ctaText; }
    public void setCtaText(String ctaText) { this.ctaText = ctaText; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getActionValue() { return actionValue; }
    public void setActionValue(String actionValue) { this.actionValue = actionValue; }
    public Long getCouponTemplateId() { return couponTemplateId; }
    public void setCouponTemplateId(Long couponTemplateId) { this.couponTemplateId = couponTemplateId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getCouponAmount() { return couponAmount; }
    public void setCouponAmount(Double couponAmount) { this.couponAmount = couponAmount; }
    public Integer getCouponValidDays() { return couponValidDays; }
    public void setCouponValidDays(Integer couponValidDays) { this.couponValidDays = couponValidDays; }
}