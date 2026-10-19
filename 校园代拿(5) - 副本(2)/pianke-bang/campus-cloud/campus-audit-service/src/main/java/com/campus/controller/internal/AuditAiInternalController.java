package com.campus.controller.internal;

import com.campus.dto.AdminAiTaskAuditResult;
import com.campus.model.DeliveryTask;
import com.campus.service.AdminAiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
public class AuditAiInternalController {

    private final AdminAiService adminAiService;

    public AuditAiInternalController(AdminAiService adminAiService) {
        this.adminAiService = adminAiService;
    }

    @GetMapping("/enabled")
    public boolean isEnabled() {
        return adminAiService.isEnabled();
    }

    @GetMapping("/auto-audit/enabled")
    public boolean isAutoAuditEnabled() {
        return adminAiService.isAutoAuditEnabled();
    }

    @PostMapping("/audit-task")
    public AdminAiTaskAuditResult auditTask(@RequestBody DeliveryTask task) {
        return adminAiService.auditTask(task);
    }

    @PostMapping("/auto-audit/enabled")
    public void setAutoAuditEnabled(@RequestBody Map<String, Boolean> body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        adminAiService.setAutoAuditEnabled(enabled);
    }
}
