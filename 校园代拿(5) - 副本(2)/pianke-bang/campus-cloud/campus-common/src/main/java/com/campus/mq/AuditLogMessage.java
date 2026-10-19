package com.campus.mq;

public class AuditLogMessage {

    private String operatorName;
    private String actionType;
    private Long targetTaskId;
    private String detail;

    public AuditLogMessage() {
    }

    public AuditLogMessage(String operatorName, String actionType, Long targetTaskId, String detail) {
        this.operatorName = operatorName;
        this.actionType = actionType;
        this.targetTaskId = targetTaskId;
        this.detail = detail;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Long getTargetTaskId() {
        return targetTaskId;
    }

    public void setTargetTaskId(Long targetTaskId) {
        this.targetTaskId = targetTaskId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
