package com.campus.dto;

import java.math.BigDecimal;

public class AlipayRechargeRequest {

    private BigDecimal amount;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
