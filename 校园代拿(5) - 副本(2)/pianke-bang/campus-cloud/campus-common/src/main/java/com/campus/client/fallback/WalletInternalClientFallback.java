package com.campus.client.fallback;

import com.campus.client.WalletInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WalletInternalClientFallback implements WalletInternalClient {

    private static final Logger log = LoggerFactory.getLogger(WalletInternalClientFallback.class);

    @Override
    public void lockTaskBounty(Map<String, Object> body) {
        log.warn("wallet lock-bounty fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public void refundTaskBounty(Map<String, Object> body) {
        log.warn("wallet refund-bounty fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public void incomeFromTask(Map<String, Object> body) {
        log.warn("wallet income-from-task fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public void recordCouponSubsidy(Map<String, Object> body) {
        log.warn("wallet coupon-subsidy fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public void compensatePublisherForTimeout(Map<String, Object> body) {
        log.warn("wallet compensate-timeout fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public void creditManualForDemo(Map<String, Object> body) {
        log.warn("wallet credit-demo fallback");
        throw new IllegalStateException("钱包服务暂时不可用");
    }

    @Override
    public Map<String, Object> agentSnapshot(String username) {
        log.warn("wallet agent-snapshot fallback, user={}", username);
        return Map.of("error", "钱包服务暂时不可用，请稍后再试");
    }

    @Override
    public Map<String, Object> agentWithdrawals(String username, Integer limit) {
        log.warn("wallet agent-withdrawals fallback, user={}", username);
        return Map.of(
                "error", "钱包服务暂时不可用，请稍后再试",
                "matched", 0,
                "shown", 0,
                "items", List.of()
        );
    }

    @Override
    public Map<String, Object> agentCoupons(String username, Integer limit) {
        log.warn("wallet agent-coupons fallback, user={}", username);
        return Map.of(
                "error", "钱包服务暂时不可用，请稍后再试",
                "matched", 0,
                "shown", 0,
                "unusedCount", 0,
                "items", List.of()
        );
    }

    @Override
    public Map<String, Object> agentTransactions(String username, Integer limit) {
        log.warn("wallet agent-transactions fallback, user={}", username);
        return Map.of(
                "error", "钱包服务暂时不可用，请稍后再试",
                "matched", 0,
                "shown", 0,
                "items", List.of()
        );
    }
}
