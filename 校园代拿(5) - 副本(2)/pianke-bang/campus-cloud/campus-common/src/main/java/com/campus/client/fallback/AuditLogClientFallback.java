package com.campus.client.fallback;

import com.campus.client.AuditLogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审计日志熔断降级：日志写入失败不影响主业务流程。
 */
@Component
public class AuditLogClientFallback implements AuditLogClient {

    private static final Logger log = LoggerFactory.getLogger(AuditLogClientFallback.class);

    @Override
    public void log(Map<String, Object> body) {
        log.warn("审计日志服务不可用，已跳过写入: {}", body);
    }
}
