package com.campus.dto;

public class TaskAppealAuditRequest {
    private boolean approve;
    /**
     * 仅当 approve=true 时生效：
     * - true: 认定服务已完成，任务按完成结算给接单员
     * - false: 认定服务未完成，但接单员无责（不扣完成率，不结算）
     */
    private boolean serviceCompleted = true;
    private String remark;

    public boolean isApprove() {
        return approve;
    }

    public void setApprove(boolean approve) {
        this.approve = approve;
    }

    public boolean isServiceCompleted() {
        return serviceCompleted;
    }

    public void setServiceCompleted(boolean serviceCompleted) {
        this.serviceCompleted = serviceCompleted;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

