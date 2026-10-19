package com.campus.dto;

import java.time.LocalDateTime;

public class CouponRedeemCodeCreateRequest {
    private Long templateId;
    /** 指定兑换码；为空则按 count 自动生成 */
    private String code;
    private Integer count;
    private Integer maxUses;
    private LocalDateTime expireAt;
    private String remark;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
