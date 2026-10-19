package com.campus.dto;

public class AdminAiAutoAuditRequest {
    /**
     * true=自动执行审核通过/驳回；false=只做 AI 判断（不落库变更）
     */
    private Boolean apply;

    public Boolean getApply() {
        return apply;
    }

    public void setApply(Boolean apply) {
        this.apply = apply;
    }
}

