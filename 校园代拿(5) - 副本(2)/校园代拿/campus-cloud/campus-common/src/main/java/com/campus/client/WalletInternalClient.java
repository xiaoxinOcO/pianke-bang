package com.campus.client;

import com.campus.client.fallback.WalletInternalClientFallback;
import com.campus.config.ServiceConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = ServiceConstants.WALLET_SERVICE,
        contextId = "walletInternalClient",
        fallback = WalletInternalClientFallback.class
)
public interface WalletInternalClient {

    @PostMapping("/internal/wallet/lock-bounty")
    void lockTaskBounty(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/wallet/refund-bounty")
    void refundTaskBounty(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/wallet/income-from-task")
    void incomeFromTask(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/wallet/coupon-subsidy")
    void recordCouponSubsidy(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/wallet/compensate-timeout")
    void compensatePublisherForTimeout(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/wallet/credit-demo")
    void creditManualForDemo(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/wallet/agent-snapshot")
    Map<String, Object> agentSnapshot(@RequestParam("username") String username);

    @GetMapping("/internal/wallet/agent-withdrawals")
    Map<String, Object> agentWithdrawals(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    );

    @GetMapping("/internal/wallet/agent-coupons")
    Map<String, Object> agentCoupons(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    );

    @GetMapping("/internal/wallet/agent-transactions")
    Map<String, Object> agentTransactions(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    );
}
