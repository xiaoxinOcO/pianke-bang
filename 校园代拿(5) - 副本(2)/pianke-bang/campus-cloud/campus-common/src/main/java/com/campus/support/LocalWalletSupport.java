package com.campus.support;

import com.campus.client.WalletInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 同库本地钱包：任务写库与钱包扣款在同一 {@code @Transactional} 内完成，不经过 Feign / Seata 全局事务。
 * 支付宝沙箱支付的赏金退款仍走 wallet-service，以便原路退回买家账号。
 */
@Component
@ConditionalOnExpression(
        "'${spring.application.name:}'.equals('campus-task-service')"
                + " && '${campus.remote.wallet:false}'.equals('false')"
)
public class LocalWalletSupport implements WalletSupport {

    private static final Logger log = LoggerFactory.getLogger(LocalWalletSupport.class);

    private final WalletBountyCore walletBountyCore;
    private final WalletInternalClient walletInternalClient;

    public LocalWalletSupport(
            WalletBountyCore walletBountyCore,
            WalletInternalClient walletInternalClient
    ) {
        this.walletBountyCore = walletBountyCore;
        this.walletInternalClient = walletInternalClient;
    }

    @Override
    public void lockTaskBounty(String publisherName, BigDecimal amount, Long taskId, String title) {
        log.info("[LocalDB] 钱包锁定赏金 -> taskId={}, publisher={}, amount={}", taskId, publisherName, amount);
        walletBountyCore.lockTaskBounty(publisherName, amount, taskId, title);
    }

    @Override
    public void refundTaskBounty(String publisherName, Long taskId, BigDecimal amount, String remark) {
        log.info("[Feign] 赏金退款（支付宝优先原路退回）-> taskId={}, publisher={}, amount={}",
                taskId, publisherName, amount);
        walletInternalClient.refundTaskBounty(Map.of(
                "publisherName", publisherName,
                "taskId", taskId,
                "amount", amount,
                "remark", remark == null ? "" : remark
        ));
    }

    @Override
    public void incomeFromTask(String claimerName, BigDecimal amount, Long taskId, String title) {
        log.info("[LocalDB] 钱包结算收入 -> taskId={}, claimer={}, amount={}", taskId, claimerName, amount);
        walletBountyCore.incomeFromTask(claimerName, amount, taskId, title);
    }

    @Override
    public void recordCouponSubsidy(String publisherName, Long taskId, BigDecimal discount, String title) {
        log.info("[LocalDB] 优惠券补贴记账 -> taskId={}, publisher={}, discount={}", taskId, publisherName, discount);
        walletBountyCore.recordCouponSubsidy(publisherName, taskId, discount, title);
    }

    @Override
    public void compensatePublisherForTimeout(
            String riderName,
            String publisherName,
            BigDecimal amount,
            Long taskId,
            String title
    ) {
        log.info("[LocalDB] 钱包超时补偿 -> taskId={}, publisher={}, rider={}, amount={}",
                taskId, publisherName, riderName, amount);
        walletBountyCore.compensatePublisherForTimeout(riderName, publisherName, amount, taskId, title);
    }

    @Override
    public void creditManualForDemo(String username, BigDecimal amount) {
        walletBountyCore.creditManualForDemo(username, amount);
    }
}
