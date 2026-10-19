package com.campus.dto;

import java.math.BigDecimal;

public class WalletOverviewDTO {
    private BigDecimal balance;
    private BigDecimal totalIncome;
    private BigDecimal totalWithdrawn;
    private BigDecimal withdrawingAmount;

    public WalletOverviewDTO(BigDecimal balance, BigDecimal totalIncome, BigDecimal totalWithdrawn, BigDecimal withdrawingAmount) {
        this.balance = balance;
        this.totalIncome = totalIncome;
        this.totalWithdrawn = totalWithdrawn;
        this.withdrawingAmount = withdrawingAmount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalWithdrawn() {
        return totalWithdrawn;
    }

    public BigDecimal getWithdrawingAmount() {
        return withdrawingAmount;
    }
}
