package com.campus.dto;

public class CouponIssueRequest {
    private Long templateId;
    private String username;
    private Integer count;
    /** 定向发券原因：活动 / 投诉补偿 / 种子用户等 */
    private String reason;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
