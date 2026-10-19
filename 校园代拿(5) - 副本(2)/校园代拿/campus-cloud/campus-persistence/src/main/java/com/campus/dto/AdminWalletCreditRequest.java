package com.campus.dto;

import java.math.BigDecimal;

public class AdminWalletCreditRequest {
    private BigDecimal amount;
    private String remark;

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
