package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_template")
public class CouponTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "min_spend", nullable = false, precision = 12, scale = 2)
    private BigDecimal minSpend = BigDecimal.ZERO;

    @Column(name = "discount_percent", precision = 8, scale = 4)
    private BigDecimal discountPercent;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock = 0;

    @Column(name = "issued_count", nullable = false)
    private Integer issuedCount = 0;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponTemplateStatus status = CouponTemplateStatus.ACTIVE;

    /** ADMIN_ONLY / PUBLIC / NEWCOMER */
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_mode", nullable = false, length = 20)
    private CouponClaimMode claimMode = CouponClaimMode.ADMIN_ONLY;

    /** 活动分组，如 NEWCOMER_PACK */
    @Column(name = "scene_code", length = 40)
    private String sceneCode;

    @Column(length = 200)
    private String description;

    /** 活动海报（URL 或压缩 dataURL） */
    @Lob
    @Column(name = "banner_image_url", columnDefinition = "MEDIUMTEXT")
    private String bannerImageUrl;

    /** 无自定义图时的默认主题：warm / green / cool / blue */
    @Column(name = "banner_theme", length = 20)
    private String bannerTheme;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CouponType getType() { return type; }
    public void setType(CouponType type) { this.type = type; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getMinSpend() { return minSpend; }
    public void setMinSpend(BigDecimal minSpend) { this.minSpend = minSpend; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public Integer getIssuedCount() { return issuedCount; }
    public void setIssuedCount(Integer issuedCount) { this.issuedCount = issuedCount; }

    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }

    public CouponTemplateStatus getStatus() { return status; }
    public void setStatus(CouponTemplateStatus status) { this.status = status; }

    public CouponClaimMode getClaimMode() { return claimMode; }
    public void setClaimMode(CouponClaimMode claimMode) { this.claimMode = claimMode; }

    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBannerImageUrl() { return bannerImageUrl; }
    public void setBannerImageUrl(String bannerImageUrl) { this.bannerImageUrl = bannerImageUrl; }

    public String getBannerTheme() { return bannerTheme; }
    public void setBannerTheme(String bannerTheme) { this.bannerTheme = bannerTheme; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
