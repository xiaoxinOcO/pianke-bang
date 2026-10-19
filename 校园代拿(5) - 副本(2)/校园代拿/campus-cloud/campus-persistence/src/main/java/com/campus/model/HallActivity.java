package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hall_activity")
public class HallActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Lob
    @Column(name = "banner_image_url", columnDefinition = "MEDIUMTEXT")
    private String bannerImageUrl;

    /** 网页端海报（与小程序尺寸不同，可单独上传） */
    @Lob
    @Column(name = "banner_image_url_web", columnDefinition = "MEDIUMTEXT")
    private String bannerImageUrlWeb;

    @Column(name = "banner_theme", length = 20)
    private String bannerTheme = "warm";

    @Column(name = "cta_text", nullable = false, length = 30)
    private String ctaText = "去看看";

    /** RIDER_APPLY / PUBLISH / COUPON_CLAIM / COUPON_CENTER / WALLET / CUSTOM_PATH */
    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "action_value", length = 200)
    private String actionValue;

    @Column(name = "coupon_template_id")
    private Long couponTemplateId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** ACTIVE / OFF */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
}
