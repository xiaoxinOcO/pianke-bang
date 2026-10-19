package com.campus.controller.internal;

import com.campus.service.AdminLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/audit")
public class AuditInternalController {

    private final AdminLogService adminLogService;

    public AuditInternalController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @PostMapping("/log")
    public void log(@RequestBody Map<String, Object> body) {
        String operator = String.valueOf(body.getOrDefault("operatorName", ""));
        String action = String.valueOf(body.getOrDefault("actionType", ""));
        Object taskIdRaw = body.get("targetTaskId");
        Long taskId = taskIdRaw instanceof Number n ? n.longValue() : null;
        if (taskId != null && taskId == 0L) {
            taskId = null;
        }
        String detail = String.valueOf(body.getOrDefault("detail", ""));
        adminLogService.log(operator, action, taskId, detail);
    }
}
