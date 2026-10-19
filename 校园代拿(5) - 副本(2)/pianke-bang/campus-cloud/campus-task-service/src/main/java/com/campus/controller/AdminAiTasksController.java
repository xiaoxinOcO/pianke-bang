package com.campus.controller;

import com.campus.dto.AdminAiAutoAuditRequest;
import com.campus.dto.AdminAiAutoAuditResponse;
import com.campus.dto.AdminAiAutoAuditEnabledRequest;
import com.campus.dto.AdminAiAutoAuditEnabledResponse;
import com.campus.dto.AdminAiTaskAuditResult;
import com.campus.dto.TaskPageResponse;
import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.client.AdminAiClient;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import com.campus.service.TaskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiTasksController {
    private final AdminAiClient adminAiClient;
    private final TaskService taskService;
    private final AdminPolicy adminPolicy;

    public AdminAiTasksController(AdminAiClient adminAiClient, TaskService taskService, AdminPolicy adminPolicy) {
        this.adminAiClient = adminAiClient;
        this.taskService = taskService;
        this.adminPolicy = adminPolicy;
    }

    @PostMapping("/tasks/auto-audit")
    public AdminAiAutoAuditResponse autoAuditPendingTasks(@RequestBody(required = false) AdminAiAutoAuditRequest request) {
        assertAdmin();

        boolean apply = request != null && Boolean.TRUE.equals(request.getApply());
        int page = 1;
        int size = 50;

        List<AdminAiTaskAuditResult> results = new ArrayList<>();
        int approvedCount = 0;
        int rejectedCount = 0;
        int errorCount = 0;
        int pendingCount = 0;

        String operator = AuthContext.getCurrentUser();
        while (true) {
            TaskPageResponse pageRes = taskService.listTasks(TaskStatus.PENDING_REVIEW, null, null, page, size);
            List<DeliveryTask> tasks = pageRes.getRecords();
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            pendingCount += tasks.size();

            for (DeliveryTask task : tasks) {
                AdminAiTaskAuditResult audit = adminAiClient.auditTask(task);
                if (audit == null) {
                    audit = new AdminAiTaskAuditResult();
                    audit.setTaskId(task.getId());
                    audit.setDecision("REJECT");
                    audit.setReason("AI 审核结果为空");
                    audit.setViolations(List.of("AI 审核失败"));
                }

                if (!apply) {
                    audit.setApplied(false);
                    results.add(audit);
                    if ("APPROVE".equals(audit.getDecision())) {
                        approvedCount++;
                    } else {
                        rejectedCount++;
                    }
                    continue;
                }

                if (audit.getError() != null && !audit.getError().isBlank()) {
                    audit.setApplied(false);
                    audit.setError(audit.getError());
                    errorCount++;
                    results.add(audit);
                    continue;
                }

                try {
                    String decision = audit.getDecision();
                    if ("APPROVE".equals(decision)) {
                        taskService.approveTask(task.getId(), operator);
                        audit.setApplied(true);
                        approvedCount++;
                    } else {
                        taskService.rejectTask(task.getId(), operator, buildRejectRemark(audit));
                        audit.setApplied(true);
                        rejectedCount++;
                    }
                } catch (Exception ex) {
                    audit.setApplied(false);
                    audit.setError("执行审核失败：" + ex.getMessage());
                    errorCount++;
                }

                results.add(audit);
            }

            page++;
            if (page > pageRes.getTotalPages() || pageRes.getTotalPages() == 0) {
                break;
            }
        }

        AdminAiAutoAuditResponse resp = new AdminAiAutoAuditResponse();
        resp.setApply(apply);
        resp.setPendingCount(pendingCount);
        resp.setApprovedCount(approvedCount);
        resp.setRejectedCount(rejectedCount);
        resp.setErrorCount(errorCount);
        resp.setResults(results);
        return resp;
    }

    private String buildRejectRemark(AdminAiTaskAuditResult audit) {
        String reason = audit == null ? "" : audit.getReason();
        if (reason == null) reason = "";
        var violations = audit == null ? null : audit.getViolations();
        if (violations != null && !violations.isEmpty()) {
            String v = String.join("；", violations.stream().filter(s -> s != null && !s.isBlank()).toList());
            if (!v.isBlank()) {
                return (reason.isBlank() ? "检测到不良信息" : reason) + "（违规点：" + v + "）";
            }
        }
        return reason.isBlank() ? "检测到不良信息，无法通过审核。请修改后重新发布/提交。" : reason;
    }

    @GetMapping("/auto-audit/enabled")
    public AdminAiAutoAuditEnabledResponse getAutoAuditEnabled() {
        assertAdmin();
        return new AdminAiAutoAuditEnabledResponse(adminAiClient.isAutoAuditEnabled());
    }

    @PostMapping("/auto-audit/enabled")
    public AdminAiAutoAuditEnabledResponse setAutoAuditEnabled(@RequestBody AdminAiAutoAuditEnabledRequest request) {
        assertAdmin();
        boolean enabled = request != null && Boolean.TRUE.equals(request.getEnabled());
        adminAiClient.setAutoAuditEnabled(java.util.Map.of("enabled", enabled));
        return new AdminAiAutoAuditEnabledResponse(adminAiClient.isAutoAuditEnabled());
    }

    private void assertAdmin() {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }
}

