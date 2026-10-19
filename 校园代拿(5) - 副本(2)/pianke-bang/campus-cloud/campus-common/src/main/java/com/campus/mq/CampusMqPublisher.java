package com.campus.mq;

import com.campus.config.CampusMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class CampusMqPublisher {

    private static final Logger log = LoggerFactory.getLogger(CampusMqPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final CampusMqProperties mqProperties;

    public CampusMqPublisher(RabbitTemplate rabbitTemplate, CampusMqProperties mqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqProperties = mqProperties;
    }

    public boolean publishTaskStatus(TaskStatusMessage message) {
        if (!mqProperties.isRealtime()) {
            return false;
        }
        rabbitTemplate.convertAndSend(CampusMqConstants.EXCHANGE, CampusMqConstants.RK_TASK_STATUS, message);
        log.info("[MQ 发送] 任务状态推送 -> exchange={}, routingKey={}, taskId={}, event={}, status={}, title={}",
                CampusMqConstants.EXCHANGE, CampusMqConstants.RK_TASK_STATUS,
                message.getTaskId(), message.getEvent(), message.getStatus(), message.getTitle());
        return true;
    }

    public boolean publishAuditLog(AuditLogMessage message) {
        if (!mqProperties.isAuditLog()) {
            return false;
        }
        rabbitTemplate.convertAndSend(CampusMqConstants.EXCHANGE, CampusMqConstants.RK_AUDIT_LOG, message);
        log.info("[MQ 发送] 审计日志 -> exchange={}, routingKey={}, actionType={}, taskId={}, operator={}",
                CampusMqConstants.EXCHANGE, CampusMqConstants.RK_AUDIT_LOG,
                message.getActionType(), message.getTargetTaskId(), message.getOperatorName());
        return true;
    }

    /**
     * 同步 RPC：发布任务冻结赏金，等待 wallet-service 处理结果后再返回。
     */
    public WalletLockRpcResponse lockTaskBountySync(WalletLockMessage message) {
        if (!mqProperties.getWallet().isLock()) {
            return WalletLockRpcResponse.fail("MQ wallet.lock 未启用");
        }
        Object reply = rabbitTemplate.convertSendAndReceive(
                CampusMqConstants.EXCHANGE,
                CampusMqConstants.RK_WALLET_LOCK,
                message
        );
        if (reply instanceof WalletLockRpcResponse response) {
            if (response.isSuccess()) {
                log.info("[MQ RPC 发送] 钱包锁定赏金成功 -> taskId={}, publisher={}, amount={}",
                        message.getTaskId(), message.getPublisherName(), message.getAmount());
            } else {
                log.warn("[MQ RPC 发送] 钱包锁定赏金失败 -> taskId={}, reason={}",
                        message.getTaskId(), response.getErrorMessage());
            }
            return response;
        }
        log.warn("[MQ RPC 发送] 钱包锁定赏金响应异常 -> taskId={}, reply={}",
                message.getTaskId(), reply);
        return WalletLockRpcResponse.fail("MQ RPC 响应为空或格式错误");
    }

    public boolean publishWalletRefund(WalletRefundMessage message) {
        if (!mqProperties.getWallet().isRefund()) {
            return false;
        }
        rabbitTemplate.convertAndSend(CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_REFUND, message);
        log.info("[MQ 发送] 钱包退还赏金 -> exchange={}, routingKey={}, taskId={}, publisher={}, amount={}",
                CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_REFUND,
                message.getTaskId(), message.getPublisherName(), message.getAmount());
        return true;
    }

    public boolean publishWalletIncome(WalletIncomeMessage message) {
        if (!mqProperties.getWallet().isIncome()) {
            return false;
        }
        rabbitTemplate.convertAndSend(CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_INCOME, message);
        log.info("[MQ 发送] 钱包结算收入 -> exchange={}, routingKey={}, taskId={}, claimer={}, amount={}",
                CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_INCOME,
                message.getTaskId(), message.getClaimerName(), message.getAmount());
        return true;
    }

    public boolean publishWalletCompensate(WalletCompensateMessage message) {
        if (!mqProperties.getWallet().isCompensate()) {
            return false;
        }
        rabbitTemplate.convertAndSend(CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_COMPENSATE, message);
        log.info("[MQ 发送] 钱包超时补偿 -> exchange={}, routingKey={}, taskId={}, publisher={}, rider={}, amount={}",
                CampusMqConstants.EXCHANGE, CampusMqConstants.RK_WALLET_COMPENSATE,
                message.getTaskId(), message.getPublisherName(), message.getRiderName(), message.getAmount());
        return true;
    }
}
