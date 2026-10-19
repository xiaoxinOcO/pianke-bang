package com.campus.client.fallback;

import com.campus.client.AdminAiClient;
import com.campus.dto.AdminAiTaskAuditResult;
import com.campus.model.DeliveryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 审核熔断降级：AI 不可用时返回错误结果，由业务层跳过自动审核。
 */
@Component
public class AdminAiClientFallback implements AdminAiClient {

    private static final Logger log = LoggerFactory.getLogger(AdminAiClientFallback.class);

    private static final String UNAVAILABLE = "AI 审核服务暂时不可用，请稍后重试或人工审核";

    @Override
    public boolean isEnabled() {
        log.warn("AI 服务熔断中，视为未启用");
        return false;
    }

    @Override
    public boolean isAutoAuditEnabled() {
        return false;
    }

    @Override
    public AdminAiTaskAuditResult auditTask(DeliveryTask task) {
        log.warn("AI 审核熔断中，跳过 taskId={}", task == null ? null : task.getId());
        AdminAiTaskAuditResult result = new AdminAiTaskAuditResult();
        if (task != null) {
            result.setTaskId(task.getId());
        }
        result.setError(UNAVAILABLE);
        return result;
    }

    @Override
    public void setAutoAuditEnabled(Map<String, Boolean> body) {
        log.warn("AI 服务熔断中，无法修改自动审核开关");
    }
}
