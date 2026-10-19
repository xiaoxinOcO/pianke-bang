package com.campus.controller.internal;

import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/internal/tasks")
public class TaskInternalController {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 8;

    private static final Set<TaskStatus> ACTIVE = Set.of(
            TaskStatus.PENDING_REVIEW,
            TaskStatus.OPEN,
            TaskStatus.CLAIMED,
            TaskStatus.OVERDUE,
            TaskStatus.PENDING_CONFIRMATION
    );
    private static final Set<TaskStatus> IN_PROGRESS = Set.of(
            TaskStatus.CLAIMED,
            TaskStatus.OVERDUE,
            TaskStatus.PENDING_CONFIRMATION
    );
    private static final Set<TaskStatus> HISTORY = Set.of(
            TaskStatus.COMPLETED,
            TaskStatus.CANCELLED,
            TaskStatus.REJECTED
    );

    private final TaskService taskService;

    public TaskInternalController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Agent 只读：当前用户任务摘要。
     * 放在 /{id}/... 之前，避免被路径变量误匹配。
     * scope: active（默认，进行中）| open（待接单）| in_progress（配送中）| history（历史）| all
     */
    @GetMapping("/agent-summary")
    public Map<String, Object> agentSummary(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "scope", required = false, defaultValue = "active") String scope,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        String me = username.trim();
        String normalizedScope = normalizeScope(scope);
        int take = clampLimit(limit);

        // Agent 内部调用只带 username，勿因本地 UserAccount 解析失败整接口 500
        List<DeliveryTask> all = taskService.listTasksForAgent(me, userId);
        List<DeliveryTask> matched = all.stream()
                .filter(t -> matchScope(t.getStatus(), normalizedScope))
                .sorted(activeFirstThenRecent())
                .toList();

        List<Map<String, Object>> items = new ArrayList<>();
        int end = Math.min(matched.size(), take);
        for (int i = 0; i < end; i++) {
            DeliveryTask t = matched.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("title", t.getTitle());
            row.put("status", t.getStatus() == null ? "" : t.getStatus().name());
            row.put("statusLabel", statusLabel(t.getStatus()));
            row.put("reward", t.getReward());
            row.put("role", resolveRole(me, t));
            items.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", me);
        data.put("scope", normalizedScope);
        data.put("scopeLabel", scopeLabel(normalizedScope));
        data.put("totalRelated", all.size());
        data.put("matched", matched.size());
        data.put("shown", items.size());
        data.put("limit", take);
        data.put("total", matched.size());
        data.put("tasks", items);
        return data;
    }

    /**
     * Agent 只读：按任务 ID 查详情（仅发布者/接单员）。
     * 放在 /{id}/... 之前，避免被路径变量误匹配。
     */
    @GetMapping("/agent-detail")
    public Map<String, Object> agentDetail(
            @RequestParam("username") String username,
            @RequestParam("taskId") Long taskId
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return taskService.agentTaskDetail(taskId, username.trim());
    }

    /** Agent 只读：任务时间线（自动确认倒计时等） */
    @GetMapping("/agent-timeline")
    public Map<String, Object> agentTimeline(
            @RequestParam("username") String username,
            @RequestParam("taskId") Long taskId
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return taskService.agentTaskTimeline(taskId, username.trim());
    }

    /** Agent 只读：已裁决申诉摘要 */
    @GetMapping("/agent-appeals")
    public Map<String, Object> agentAppeals(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return taskService.agentAppeals(username.trim(), userId, limit);
    }

    /** Agent 只读：本周加急配额 */
    @GetMapping("/agent-urgent-quota")
    public Map<String, Object> agentUrgentQuota(
            @RequestParam("username") String username,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return taskService.agentUrgentQuota(username.trim(), userId);
    }

    @GetMapping("/{id}/alipay-bounty")
    public Map<String, Object> getAlipayBountyInfo(
            @PathVariable("id") Long id,
            @RequestParam("username") String username
    ) {
        return taskService.getAlipayBountyInfo(id, username);
    }

    @PostMapping("/{id}/prepare-alipay-bounty")
    public Map<String, Object> prepareAlipayBounty(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        return taskService.prepareAlipayBountyPayment(id, body);
    }

    @PostMapping("/{id}/confirm-alipay-bounty")
    public void confirmAlipayBounty(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        taskService.confirmAlipayBountyPayment(id, body);
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "active";
        }
        String s = scope.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "open", "pending", "waiting" -> "open";
            case "in_progress", "progress", "claimed", "delivering" -> "in_progress";
            case "history", "past", "done" -> "history";
            case "all" -> "all";
            default -> "active";
        };
    }

    private static boolean matchScope(TaskStatus status, String scope) {
        if (status == null) {
            return false;
        }
        return switch (scope) {
            case "open" -> status == TaskStatus.OPEN;
            case "in_progress" -> IN_PROGRESS.contains(status);
            case "history" -> HISTORY.contains(status);
            case "all" -> true;
            default -> ACTIVE.contains(status);
        };
    }

    private static String scopeLabel(String scope) {
        return switch (scope) {
            case "open" -> "待接单";
            case "in_progress" -> "进行中";
            case "history" -> "历史";
            case "all" -> "全部";
            default -> "进行中";
        };
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, limit);
    }

    private static Comparator<DeliveryTask> activeFirstThenRecent() {
        return (a, b) -> {
            int pa = statusPriority(a.getStatus());
            int pb = statusPriority(b.getStatus());
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            var ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            var tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            if (ta == null && tb == null) {
                return Long.compare(idOf(b), idOf(a));
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            int cmp = tb.compareTo(ta);
            return cmp != 0 ? cmp : Long.compare(idOf(b), idOf(a));
        };
    }

    private static int statusPriority(TaskStatus status) {
        if (status == null) {
            return 99;
        }
        return switch (status) {
            case OVERDUE -> 0;
            case PENDING_CONFIRMATION -> 1;
            case CLAIMED -> 2;
            case OPEN -> 3;
            case PENDING_REVIEW -> 4;
            case REJECTED -> 5;
            case COMPLETED -> 6;
            case CANCELLED -> 7;
        };
    }

    private static long idOf(DeliveryTask t) {
        return t.getId() == null ? 0L : t.getId();
    }

    private static String resolveRole(String me, DeliveryTask t) {
        String pub = t.getPublisherName() == null ? "" : t.getPublisherName().trim();
        String claim = t.getClaimerName() == null ? "" : t.getClaimerName().trim();
        if (me.equalsIgnoreCase(pub)) {
            return "publisher";
        }
        if (me.equalsIgnoreCase(claim)) {
            return "claimer";
        }
        return "related";
    }

    private static String statusLabel(TaskStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING_REVIEW -> "待审核";
            case REJECTED -> "已驳回";
            case OPEN -> "待接单";
            case CLAIMED -> "配送中";
            case OVERDUE -> "已逾期";
            case PENDING_CONFIRMATION -> "待确认";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }
}
