package com.campus.client;

import com.campus.client.fallback.AuditLogClientFallback;
import com.campus.config.ServiceConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = ServiceConstants.AUDIT_SERVICE, contextId = "auditLogClient", fallback = AuditLogClientFallback.class)
public interface AuditLogClient {

    @PostMapping("/internal/audit/log")
    void log(@RequestBody Map<String, Object> body);
}
