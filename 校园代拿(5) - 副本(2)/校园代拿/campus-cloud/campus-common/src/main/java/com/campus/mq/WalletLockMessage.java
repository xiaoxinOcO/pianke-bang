package com.campus.mq;

import java.math.BigDecimal;

public class WalletLockMessage {

    private String publisherName;
    private BigDecimal amount;
    private Long taskId;
    private String title;

    public WalletLockMessage() {
    }

    public WalletLockMessage(String publisherName, BigDecimal amount, Long taskId, String title) {
        this.publisherName = publisherName;
        this.amount = amount;
        this.taskId = taskId;
        this.title = title;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
