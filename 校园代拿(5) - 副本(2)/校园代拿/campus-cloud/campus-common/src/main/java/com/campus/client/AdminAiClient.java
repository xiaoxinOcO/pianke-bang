package com.campus.client;

import com.campus.client.fallback.AdminAiClientFallback;
import com.campus.config.ServiceConstants;
import com.campus.dto.AdminAiTaskAuditResult;
import com.campus.model.DeliveryTask;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ServiceConstants.AUDIT_SERVICE, contextId = "adminAiClient", fallback = AdminAiClientFallback.class)
public interface AdminAiClient {

    @GetMapping("/internal/ai/enabled")
    boolean isEnabled();

    @GetMapping("/internal/ai/auto-audit/enabled")
    boolean isAutoAuditEnabled();

    @PostMapping("/internal/ai/audit-task")
    AdminAiTaskAuditResult auditTask(@RequestBody DeliveryTask task);

    @PostMapping("/internal/ai/auto-audit/enabled")
    void setAutoAuditEnabled(@RequestBody java.util.Map<String, Boolean> body);
}
