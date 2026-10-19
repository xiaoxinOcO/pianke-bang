package com.campus.service;

import com.campus.client.AdminAiClient;
import com.campus.dto.TaskPageResponse;
import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.security.AdminPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AdminAiAutoAuditScheduler {
    private final AdminAiClient adminAiClient;
    private final TaskService taskService;
    private final AdminPolicy adminPolicy;

    private final String operatorName;

    public AdminAiAutoAuditScheduler(
            AdminAiClient adminAiClient,
            TaskService taskService,
            AdminPolicy adminPolicy,
            @Value("${app.admin.usernames:admin}") String adminUsernamesCsv
    ) {
        this.adminAiClient = adminAiClient;
        this.taskService = taskService;
        this.adminPolicy = adminPolicy;
        this.operatorName = parseFirstAdmin(adminUsernamesCsv);
    }

    @Scheduled(fixedDelayString = "${app.llm.auto-audit-interval-ms:${app.ollama.auto-audit-interval-ms:60000}}")
    public void run() {
        if (!adminAiClient.isAutoAuditEnabled()) {
            return;
        }
        if (!adminAiClient.isEnabled()) {
            return;
        }
        if (operatorName == null || operatorName.isBlank() || !adminPolicy.isAdmin(operatorName)) {
            return;
        }

        // 单次最多处理 50 条，避免一轮跑太久阻塞请求线程
        int page = 1;
        int size = 50;
        int maxTasks = 50;
        int processed = 0;

        while (processed < maxTasks) {
            TaskPageResponse pageRes = taskService.listTasks(TaskStatus.PENDING_REVIEW, null, null, page, size);
            List<DeliveryTask> tasks = pageRes.getRecords();
            if (tasks == null || tasks.isEmpty()) {
                break;
            }

            List<Long> taskIds = tasks.stream().map(DeliveryTask::getId).collect(Collectors.toList());
            for (Long id : taskIds) {
                if (processed >= maxTasks) break;
                DeliveryTask task = tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
                var audit = adminAiClient.auditTask(task);
                if (audit == null) {
                    // 保守兜底：AI 返回为空直接驳回，并使用默认原因
                    taskService.rejectTask(id, operatorName);
                    processed++;
                    continue;
                }

                if (audit.getError() != null && !audit.getError().isBlank()) {
                    // AI 审核失败：不执行驳回，避免误杀；下次定时继续处理
                    processed++;
                    continue;
                }

                if ("APPROVE".equals(audit.getDecision())) {
                    taskService.approveTask(id, operatorName);
                } else {
                    taskService.rejectTask(id, operatorName, buildRejectRemark(audit));
                }
                processed++;
            }

            page++;
            if (page > pageRes.getTotalPages() || pageRes.getTotalPages() == 0) {
                break;
            }
        }
    }

    private String parseFirstAdmin(String csv) {
        if (csv == null || csv.isBlank()) return "admin";
        String[] parts = csv.split(",");
        for (String p : parts) {
            if (p != null && !p.trim().isBlank()) return p.trim();
        }
        return "admin";
    }

    private String buildRejectRemark(com.campus.dto.AdminAiTaskAuditResult audit) {
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
}

