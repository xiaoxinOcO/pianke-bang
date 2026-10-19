package com.campus.controller;

import java.util.List;
import java.util.Map;
import com.campus.dto.TaskCompleteRequest;
import com.campus.dto.TaskCreateRequest;
import com.campus.dto.TaskAppealAuditRequest;
import com.campus.dto.TaskPageResponse;
import com.campus.dto.TaskRateRequest;
import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.security.AuthContext;
import com.campus.service.TaskService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostConstruct
    public void initData() {
        taskService.seedDemoData();
    }

    @GetMapping
    public TaskPageResponse listTasks(
            @RequestParam(value = "status", required = false) TaskStatus status,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return taskService.listTasks(status, taskType, keyword, page, size);
    }

    @GetMapping("/urgent-quota")
    public Map<String, Object> urgentQuota() {
        return taskService.getUrgentQuota(AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }

    /** 大厅侧栏：完成率等公开概览（无需登录） */
    @GetMapping("/hall-stats")
    public Map<String, Object> hallStats(@RequestParam(value = "days", defaultValue = "30") int days) {
        return taskService.getHallStats(days);
    }

    @GetMapping("/mine")
    public List<DeliveryTask> listMyTasks() {
        return taskService.listTasksForCurrentUser(AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryTask createTask(@RequestBody TaskCreateRequest request) {
        return taskService.createTask(request, currentOperator());
    }

    @PostMapping("/{id}/claim")
    public DeliveryTask claimTask(@PathVariable("id") Long id) {
        return taskService.claimTask(id, AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }

    @PostMapping("/{id}/complete")
    public DeliveryTask completeTask(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TaskCompleteRequest request) {
        return taskService.completeTask(id, request, currentOperator());
    }

    @PostMapping("/{id}/cancel")
    public DeliveryTask cancelTask(@PathVariable("id") Long id) {
        return taskService.cancelTask(id, currentOperator());
    }

    @PostMapping("/{id}/confirm")
    public DeliveryTask confirmTask(@PathVariable("id") Long id) {
        return taskService.confirmTask(id, currentOperator());
    }

    @PostMapping("/{id}/rate")
    public DeliveryTask rateTask(@PathVariable("id") Long id, @RequestBody TaskRateRequest request) {
        return taskService.rateClaimer(id, request, currentOperator());
    }

    @PostMapping("/{id}/approve")
    public DeliveryTask approveTask(@PathVariable("id") Long id) {
        return taskService.approveTask(id, currentOperator());
    }

    @PostMapping("/{id}/reject")
    public DeliveryTask rejectTask(@PathVariable("id") Long id) {
        return taskService.rejectTask(id, currentOperator());
    }

    @PutMapping("/{id}/resubmit")
    public DeliveryTask resubmitTask(@PathVariable("id") Long id, @RequestBody TaskCreateRequest request) {
        return taskService.resubmitRejectedTask(id, request, currentOperator());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable("id") Long id) {
        taskService.deleteTask(id, currentOperator());
    }

    @PostMapping("/{id}/appeal/audit")
    public DeliveryTask auditAppeal(@PathVariable("id") Long id, @RequestBody TaskAppealAuditRequest request) {
        boolean approve = request != null && request.isApprove();
        boolean serviceCompleted = request == null || request.isServiceCompleted();
        String remark = request == null ? "" : request.getRemark();
        return taskService.auditAppeal(id, approve, serviceCompleted, remark, currentOperator());
    }

    /** 仅任务发布者可获取接单员电话，用于小程序拨号 */
    @GetMapping("/{id}/claimer-phone")
    public Map<String, String> claimerPhone(@PathVariable("id") Long id) {
        return taskService.getClaimerPhoneForPublisher(
                id,
                AuthContext.getCurrentUser(),
                AuthContext.getCurrentUserId()
        );
    }

    private String currentOperator() {
        return taskService.resolveOperatorUsername(AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }
}
