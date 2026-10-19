package com.campus.dto;

public class UserPublicProfileDTO {
    private String username;
    private String avatarUrl;
    private Double publisherAverageRating;
    private Long publisherRatingCount;
    private Double claimerAverageRating;
    private Long claimerRatingCount;
    private Long claimedTaskCount;
    private Boolean riderApproved;
    private String gender;
    private String phone;
    private Boolean customerServiceStaff;

    public UserPublicProfileDTO() {
    }

    public UserPublicProfileDTO(
            String username,
            String avatarUrl,
            Double publisherAverageRating,
            Long publisherRatingCount,
            Double claimerAverageRating,
            Long claimerRatingCount,
            Long claimedTaskCount,
            Boolean riderApproved,
            String gender,
            String phone,
            Boolean customerServiceStaff
    ) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.publisherAverageRating = publisherAverageRating;
        this.publisherRatingCount = publisherRatingCount;
        this.claimerAverageRating = claimerAverageRating;
        this.claimerRatingCount = claimerRatingCount;
        this.claimedTaskCount = claimedTaskCount;
        this.riderApproved = riderApproved;
        this.gender = gender;
        this.phone = phone;
        this.customerServiceStaff = customerServiceStaff;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Double getPublisherAverageRating() {
        return publisherAverageRating;
    }

    public void setPublisherAverageRating(Double publisherAverageRating) {
        this.publisherAverageRating = publisherAverageRating;
    }

    public Long getPublisherRatingCount() {
        return publisherRatingCount;
    }

    public void setPublisherRatingCount(Long publisherRatingCount) {
        this.publisherRatingCount = publisherRatingCount;
    }

    public Double getClaimerAverageRating() {
        return claimerAverageRating;
    }

    public void setClaimerAverageRating(Double claimerAverageRating) {
        this.claimerAverageRating = claimerAverageRating;
    }

    public Long getClaimerRatingCount() {
        return claimerRatingCount;
    }

    public void setClaimerRatingCount(Long claimerRatingCount) {
        this.claimerRatingCount = claimerRatingCount;
    }

    public Long getClaimedTaskCount() {
        return claimedTaskCount;
    }

    public void setClaimedTaskCount(Long claimedTaskCount) {
        this.claimedTaskCount = claimedTaskCount;
    }

    public Boolean getRiderApproved() {
        return riderApproved;
    }

    public void setRiderApproved(Boolean riderApproved) {
        this.riderApproved = riderApproved;
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

    public Boolean getCustomerServiceStaff() {
        return customerServiceStaff;
    }

    public void setCustomerServiceStaff(Boolean customerServiceStaff) {
        this.customerServiceStaff = customerServiceStaff;
    }
}
