package com.campus.dto;

import java.util.List;

public class AdminAiAutoAuditResponse {
    private Boolean apply;
    private Integer pendingCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private Integer errorCount;
    private List<AdminAiTaskAuditResult> results;

    public Boolean getApply() {
        return apply;
    }

    public void setApply(Boolean apply) {
        this.apply = apply;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount) {
        this.pendingCount = pendingCount;
    }

    public Integer getApprovedCount() {
        return approvedCount;
    }

    public void setApprovedCount(Integer approvedCount) {
        this.approvedCount = approvedCount;
    }

    public Integer getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(Integer rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public List<AdminAiTaskAuditResult> getResults() {
        return results;
    }

    public void setResults(List<AdminAiTaskAuditResult> results) {
        this.results = results;
    }
}

