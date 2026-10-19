package com.campus.support;

import com.campus.client.WalletInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 钱包跨服务调用：仅 task-service 在 campus.remote.wallet=true 时走 Feign 调 wallet-service。
 */
@Component
@ConditionalOnExpression(
        "'${spring.application.name:}'.equals('campus-task-service')"
                + " && '${campus.remote.wallet:false}'.equals('true')"
)
public class RemoteWalletSupport implements WalletSupport {

    private static final Logger log = LoggerFactory.getLogger(RemoteWalletSupport.class);

    private final WalletInternalClient walletInternalClient;

    public RemoteWalletSupport(WalletInternalClient walletInternalClient) {
        this.walletInternalClient = walletInternalClient;
    }

    @Override
    public void lockTaskBounty(String publisherName, BigDecimal amount, Long taskId, String title) {
        log.info("[Feign+Seata] 钱包锁定赏金 -> taskId={}, publisher={}, amount={}", taskId, publisherName, amount);
        walletInternalClient.lockTaskBounty(Map.of(
                "publisherName", publisherName,
                "amount", amount,
                "taskId", taskId,
                "title", title == null ? "" : title
        ));
    }

    @Override
    public void refundTaskBounty(String publisherName, Long taskId, BigDecimal amount, String remark) {
        log.info("[Feign+Seata] 钱包退还赏金 -> taskId={}, publisher={}, amount={}", taskId, publisherName, amount);
        walletInternalClient.refundTaskBounty(Map.of(
                "publisherName", publisherName,
                "taskId", taskId,
                "amount", amount,
                "remark", remark == null ? "" : remark
        ));
    }

    @Override
    public void incomeFromTask(String claimerName, BigDecimal amount, Long taskId, String title) {
        log.info("[Feign+Seata] 钱包结算收入 -> taskId={}, claimer={}, amount={}", taskId, claimerName, amount);
        walletInternalClient.incomeFromTask(Map.of(
                "claimerName", claimerName,
                "amount", amount,
                "taskId", taskId,
                "title", title == null ? "" : title
        ));
    }

    @Override
    public void recordCouponSubsidy(String publisherName, Long taskId, BigDecimal discount, String title) {
        log.info("[Feign] 优惠券补贴记账 -> taskId={}, publisher={}, discount={}", taskId, publisherName, discount);
        walletInternalClient.recordCouponSubsidy(Map.of(
                "publisherName", publisherName,
                "taskId", taskId,
                "discount", discount,
                "title", title == null ? "" : title
        ));
    }

    @Override
    public void compensatePublisherForTimeout(String riderName, String publisherName, BigDecimal amount, Long taskId, String title) {
        log.info("[Feign+Seata] 钱包超时补偿 -> taskId={}, publisher={}, rider={}, amount={}",
                taskId, publisherName, riderName, amount);
        walletInternalClient.compensatePublisherForTimeout(Map.of(
                "riderName", riderName,
                "publisherName", publisherName,
                "amount", amount,
                "taskId", taskId,
                "title", title == null ? "" : title
        ));
    }

    @Override
    public void creditManualForDemo(String username, BigDecimal amount) {
        walletInternalClient.creditManualForDemo(Map.of(
                "username", username,
                "amount", amount
        ));
    }
}
