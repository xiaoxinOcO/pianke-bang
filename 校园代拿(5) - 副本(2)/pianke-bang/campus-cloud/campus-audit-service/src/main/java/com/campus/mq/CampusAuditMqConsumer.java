package com.campus.mq;

import com.campus.service.AdminLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.mq.enabled", havingValue = "true")
public class CampusAuditMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(CampusAuditMqConsumer.class);

    private final AdminLogService adminLogService;

    public CampusAuditMqConsumer(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @RabbitListener(queues = CampusMqConstants.QUEUE_AUDIT_EVENT)
    public void onAuditEvent(AuditLogMessage message) {
        if (message == null) {
            return;
        }
        if (message.getActionType() == null || message.getActionType().isBlank()) {
            return;
        }
        log.info("[MQ 消费] 审计日志 -> queue={}, actionType={}, taskId={}, operator={}",
                CampusMqConstants.QUEUE_AUDIT_EVENT,
                message.getActionType(),
                message.getTargetTaskId(),
                message.getOperatorName());
        adminLogService.log(
                message.getOperatorName() == null ? "" : message.getOperatorName(),
                message.getActionType(),
                message.getTargetTaskId(),
                message.getDetail() == null ? "" : message.getDetail()
        );
    }
}
