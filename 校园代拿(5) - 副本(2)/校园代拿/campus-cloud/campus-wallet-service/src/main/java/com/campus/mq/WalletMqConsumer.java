package com.campus.mq;

import com.campus.config.CampusMqProperties;
import com.campus.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.mq.enabled", havingValue = "true")
public class WalletMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletMqConsumer.class);

    private final WalletService walletService;
    private final CampusMqProperties mqProperties;

    public WalletMqConsumer(WalletService walletService, CampusMqProperties mqProperties) {
        this.walletService = walletService;
        this.mqProperties = mqProperties;
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_WALLET_LOCK)
    public WalletLockRpcResponse onLock(WalletLockMessage message) {
        if (!mqProperties.getWallet().isLock()) {
            log.warn("[MQ RPC 消费] campus.mq.wallet.lock=false，丢弃积压消息 -> taskId={}", message.getTaskId());
            return WalletLockRpcResponse.fail("MQ wallet.lock 已关闭，请使用 Feign+Seata");
        }
        log.info("[MQ RPC 消费] 钱包锁定赏金 -> queue={}, taskId={}, publisher={}, amount={}",
                CampusMqConstants.QUEUE_WALLET_LOCK,
                message.getTaskId(), message.getPublisherName(), message.getAmount());
        try {
            walletService.lockTaskBounty(
                    message.getPublisherName(),
                    message.getAmount(),
                    message.getTaskId(),
                    message.getTitle()
            );
            return WalletLockRpcResponse.ok();
        } catch (Exception ex) {
            log.warn("[MQ RPC 消费] 钱包锁定赏金失败 -> taskId={}, reason={}",
                    message.getTaskId(), ex.getMessage());
            return WalletLockRpcResponse.fail(ex.getMessage());
        }
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_WALLET_REFUND)
    public void onRefund(WalletRefundMessage message) {
        if (!mqProperties.getWallet().isRefund()) {
            log.warn("[MQ 消费] campus.mq.wallet.refund=false，丢弃积压退款消息 -> taskId={}", message.getTaskId());
            return;
        }
        log.info("[MQ 消费] 钱包退还赏金 -> queue={}, taskId={}, publisher={}, amount={}",
                CampusMqConstants.QUEUE_WALLET_REFUND,
                message.getTaskId(), message.getPublisherName(), message.getAmount());
        walletService.refundTaskBounty(
                message.getPublisherName(),
                message.getTaskId(),
                message.getAmount(),
                message.getRemark()
        );
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_WALLET_INCOME)
    public void onIncome(WalletIncomeMessage message) {
        if (!mqProperties.getWallet().isIncome()) {
            log.warn("[MQ 消费] campus.mq.wallet.income=false，丢弃积压结算消息 -> taskId={}", message.getTaskId());
            return;
        }
        log.info("[MQ 消费] 钱包结算收入 -> queue={}, taskId={}, claimer={}, amount={}",
                CampusMqConstants.QUEUE_WALLET_INCOME,
                message.getTaskId(), message.getClaimerName(), message.getAmount());
        walletService.incomeFromTask(
                message.getClaimerName(),
                message.getAmount(),
                message.getTaskId(),
                message.getTitle()
        );
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_WALLET_COMPENSATE)
    public void onCompensate(WalletCompensateMessage message) {
        if (!mqProperties.getWallet().isCompensate()) {
            log.warn("[MQ 消费] campus.mq.wallet.compensate=false，丢弃积压补偿消息 -> taskId={}", message.getTaskId());
            return;
        }
        log.info("[MQ 消费] 钱包超时补偿 -> queue={}, taskId={}, publisher={}, rider={}, amount={}",
                CampusMqConstants.QUEUE_WALLET_COMPENSATE,
                message.getTaskId(), message.getPublisherName(), message.getRiderName(), message.getAmount());
        walletService.compensatePublisherForTimeout(
                message.getRiderName(),
                message.getPublisherName(),
                message.getAmount(),
                message.getTaskId(),
                message.getTitle()
        );
    }
}
