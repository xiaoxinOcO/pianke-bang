package com.campus.support;

import com.campus.client.AuditLogClient;
import com.campus.config.CampusMqProperties;
import com.campus.mq.AuditLogMessage;
import com.campus.mq.CampusMqPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "campus.remote.audit-log", havingValue = "true")
public class RemoteAuditLogSupport {

    private static final Logger log = LoggerFactory.getLogger(RemoteAuditLogSupport.class);

    private final AuditLogClient auditLogClient;
    private final ObjectProvider<CampusMqPublisher> mqPublisher;
    private final CampusMqProperties mqProperties;

    public RemoteAuditLogSupport(
            AuditLogClient auditLogClient,
            ObjectProvider<CampusMqPublisher> mqPublisher,
            CampusMqProperties mqProperties
    ) {
        this.auditLogClient = auditLogClient;
        this.mqPublisher = mqPublisher;
        this.mqProperties = mqProperties;
    }

    public void log(String operatorName, String actionType, Long targetTaskId, String detail) {
        CampusMqPublisher publisher = mqPublisher.getIfAvailable();
        if (publisher != null && mqProperties.isAuditLog()) {
            try {
                if (publisher.publishAuditLog(new AuditLogMessage(
                        operatorName == null ? "" : operatorName,
                        actionType == null ? "" : actionType,
                        targetTaskId,
                        detail == null ? "" : detail
                ))) {
                    return;
                }
            } catch (Exception ex) {
                log.warn("MQ 审计日志发送失败，回退 Feign: actionType={}, taskId={}, reason={}",
                        actionType, targetTaskId, ex.getMessage());
            }
        }

        log.info("[Feign 调用] 审计日志 -> actionType={}, taskId={}, operator={}",
                actionType, targetTaskId, operatorName);
        try {
            auditLogClient.log(Map.of(
                    "operatorName", operatorName == null ? "" : operatorName,
                    "actionType", actionType == null ? "" : actionType,
                    "targetTaskId", targetTaskId == null ? 0L : targetTaskId,
                    "detail", detail == null ? "" : detail
            ));
        } catch (Exception ex) {
            log.warn("审计日志写入失败，已跳过: actionType={}, taskId={}, reason={}",
                    actionType, targetTaskId, ex.getMessage());
        }
    }
}
