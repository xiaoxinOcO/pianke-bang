package com.campus.dto;

import java.math.BigDecimal;

public class AlipayWithdrawRequest {
    private BigDecimal amount;
    /** 沙箱买家登录号，如 xxx@sandbox.com */
    private String alipayAccount;
    /** 沙箱买家姓名，通常为「沙箱环境」 */
    private String accountName;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAlipayAccount() {
        return alipayAccount;
    }

    public void setAlipayAccount(String alipayAccount) {
        this.alipayAccount = alipayAccount;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}
