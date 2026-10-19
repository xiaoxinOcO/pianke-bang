package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.mq")
public class CampusMqProperties {

    private boolean enabled = false;
    private boolean realtime = false;
    private boolean auditLog = false;
    private Wallet wallet = new Wallet();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRealtime() {
        return realtime;
    }

    public void setRealtime(boolean realtime) {
        this.realtime = realtime;
    }

    public boolean isAuditLog() {
        return auditLog;
    }

    public void setAuditLog(boolean auditLog) {
        this.auditLog = auditLog;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public static class Wallet {
        private boolean lock = false;
        private boolean refund = false;
        private boolean income = false;
        private boolean compensate = false;

        public boolean isLock() {
            return lock;
        }

        public void setLock(boolean lock) {
            this.lock = lock;
        }

        public boolean isRefund() {
            return refund;
        }

        public void setRefund(boolean refund) {
            this.refund = refund;
        }

        public boolean isIncome() {
            return income;
        }

        public void setIncome(boolean income) {
            this.income = income;
        }

        public boolean isCompensate() {
            return compensate;
        }

        public void setCompensate(boolean compensate) {
            this.compensate = compensate;
        }
    }
}
