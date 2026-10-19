package com.campus.mq;

import java.math.BigDecimal;

public class WalletRefundMessage {

    private String publisherName;
    private Long taskId;
    private BigDecimal amount;
    private String remark;

    public WalletRefundMessage() {
    }

    public WalletRefundMessage(String publisherName, Long taskId, BigDecimal amount, String remark) {
        this.publisherName = publisherName;
        this.taskId = taskId;
        this.amount = amount;
        this.remark = remark;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
