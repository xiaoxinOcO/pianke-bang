package com.campus.mq;

import java.math.BigDecimal;

public class WalletIncomeMessage {

    private String claimerName;
    private BigDecimal amount;
    private Long taskId;
    private String title;

    public WalletIncomeMessage() {
    }

    public WalletIncomeMessage(String claimerName, BigDecimal amount, Long taskId, String title) {
        this.claimerName = claimerName;
        this.amount = amount;
        this.taskId = taskId;
        this.title = title;
    }

    public String getClaimerName() {
        return claimerName;
    }

    public void setClaimerName(String claimerName) {
        this.claimerName = claimerName;
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
