package com.campus.service;

import com.campus.dto.TaskCompleteRequest;
import com.campus.dto.TaskCreateRequest;
import com.campus.dto.TaskPageResponse;
import com.campus.dto.TaskRateRequest;
import com.campus.elasticsearch.TaskElasticsearchService;
import com.campus.model.DeliveryTask;
import com.campus.model.UserAccount;
import com.campus.model.TaskStatus;
import com.campus.repository.DeliveryTaskRepository;
import com.campus.security.AdminPolicy;
import com.campus.repository.UserAccountRepository;
import com.campus.rules.CreditRules;
import com.campus.support.AlipayBountyOrderSupport;
import com.campus.support.CouponCore;
import com.campus.support.CouponQuote;
import com.campus.support.RemoteAuditLogSupport;
import com.campus.support.RemoteSystemMessageSupport;
import com.campus.support.TaskNotificationSupport;
import com.campus.support.WalletSupport;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TaskService {
    private static final String DEFAULT_TASK_TYPE = "EXPRESS";
    private static final Set<String> SUPPORTED_TASK_TYPES = new LinkedHashSet<>(Set.of(
            "EXPRESS", "FOOD", "PRINT", "QUEUE", "OTHER"
    ));
    private static final int URGENT_WEEKLY_LIMIT = 3;
    private static final int URGENT_BOOST_MINUTES = 30;
    private static final BigDecimal URGENT_FEE_MIN = new BigDecimal("1.00");
    private static final BigDecimal URGENT_FEE_MAX = new BigDecimal("20.00");
    private static final BigDecimal URGENT_STEP = new BigDecimal("0.50");
    private static final BigDecimal URGENT_RIDER_RATIO = new BigDecimal("0.80");

    private final DeliveryTaskRepository deliveryTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final RemoteAuditLogSupport remoteAuditLog;
    private final WalletSupport walletSupport;
    private final CouponCore couponCore;
    private final AlipayBountyOrderSupport alipayBountyOrderSupport;
    private final AdminPolicy adminPolicy;
    private final TaskNotificationSupport taskNotification;
    private final ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport;
    private final Optional<TaskElasticsearchService> taskElasticsearchService;
    private final ObjectProvider<TaskService> self;
    private final long pendingConfirmAutoMinutes;
    private final boolean autoApproveAfterBounty;
    private final long abandonedPendingReviewHours;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskService(
            DeliveryTaskRepository deliveryTaskRepository,
            UserAccountRepository userAccountRepository,
            RemoteAuditLogSupport remoteAuditLog,
            WalletSupport walletSupport,
            CouponCore couponCore,
            AlipayBountyOrderSupport alipayBountyOrderSupport,
            AdminPolicy adminPolicy,
            TaskNotificationSupport taskNotification,
            ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport,
            ObjectProvider<TaskService> self,
            @Autowired(required = false) TaskElasticsearchService taskElasticsearchService,
            @Value("${app.task.pending-confirm-auto-minutes:30}") long pendingConfirmAutoMinutes,
            @Value("${app.task.auto-approve-after-bounty:false}") boolean autoApproveAfterBounty,
            @Value("${app.task.abandoned-pending-review-hours:2}") long abandonedPendingReviewHours
    ) {
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.remoteAuditLog = remoteAuditLog;
        this.walletSupport = walletSupport;
        this.couponCore = couponCore;
        this.alipayBountyOrderSupport = alipayBountyOrderSupport;
        this.adminPolicy = adminPolicy;
        this.taskNotification = taskNotification;
        this.systemMessageSupport = systemMessageSupport;
        this.self = self;
        this.taskElasticsearchService = Optional.ofNullable(taskElasticsearchService);
        this.pendingConfirmAutoMinutes = pendingConfirmAutoMinutes;
        this.autoApproveAfterBounty = autoApproveAfterBounty;
        this.abandonedPendingReviewHours = abandonedPendingReviewHours;
    }

    public TaskPageResponse listTasks(TaskStatus status, String taskType, String keyword, int page, int size) {
        markOverdueTasks();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));

        // 排序在 JPQL 中完成（加急加权窗）
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        String normalizedType = normalizeTaskTypeForFilter(taskType);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LocalDateTime urgentSince = LocalDateTime.now().minusMinutes(URGENT_BOOST_MINUTES);

        if (!normalizedKeyword.isEmpty()) {
            TaskPageResponse esResult = taskElasticsearchService
                    .map(es -> es.searchTasks(status, normalizedType, normalizedKeyword, safePage, safeSize))
                    .orElse(null);
            if (esResult != null && esResult.getTotalElements() > 0) {
                return esResult;
            }
        }

        Page<DeliveryTask> resultPage = deliveryTaskRepository.searchTasksHallOrdered(
                status, normalizedType, normalizedKeyword, urgentSince, pageable);

        return new TaskPageResponse(
                resultPage.getContent(),
                safePage,
                safeSize,
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    /** 当前用户本周加急配额 */
    public Map<String, Object> getUrgentQuota(String currentUser, Long currentUserId) {
        String operator = resolveUserAccount(currentUser, currentUserId).getUsername();
        LocalDateTime weekStart = currentWeekStart();
        long used = deliveryTaskRepository.countUrgentUsedThisWeek(operator, weekStart);
        long remaining = Math.max(0, URGENT_WEEKLY_LIMIT - used);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weeklyLimit", URGENT_WEEKLY_LIMIT);
        out.put("used", used);
        out.put("remaining", remaining);
        out.put("weekStart", weekStart.toString());
        out.put("feeMin", URGENT_FEE_MIN);
        out.put("feeMax", URGENT_FEE_MAX);
        out.put("feeStep", URGENT_STEP);
        out.put("boostMinutes", URGENT_BOOST_MINUTES);
        out.put("riderRatio", URGENT_RIDER_RATIO);
        return out;
    }

    /**
     * 大厅公开统计：完成率 = 近 N 天已完成 / (已完成 + 接单后取消)。
     * 无人接单就取消的大厅任务不计入分母。
     */
    public Map<String, Object> getHallStats(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        long completed = deliveryTaskRepository.countByStatusAndUpdatedAtGreaterThanEqual(TaskStatus.COMPLETED, since);
        long cancelled = deliveryTaskRepository.countClaimedCancelledSince(since);
        long settled = completed + cancelled;
        Double rate = settled <= 0 ? null : Math.round(completed * 1000.0 / settled) / 10.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", safeDays);
        out.put("completedCount", completed);
        out.put("cancelledCount", cancelled);
        out.put("settledCount", settled);
        out.put("completionRate", rate);
        return out;
    }

    public List<DeliveryTask> listTasksForCurrentUser(String currentUser, Long currentUserId) {
        markOverdueTasks();
        String operator = resolveUserAccount(currentUser, currentUserId).getUsername();
        return listTasksByOperator(operator);
    }

    /**
     * Agent 内部只读查询：UserAccount 解析失败时仍用传入 username 查任务，避免整接口 500。
     */
    public List<DeliveryTask> listTasksForAgent(String currentUser, Long currentUserId) {
        markOverdueTasks();
        String operator = currentUser == null ? "" : currentUser.trim();
        if (operator.isBlank() && currentUserId == null) {
            return List.of();
        }
        try {
            operator = resolveUserAccount(currentUser, currentUserId).getUsername();
        } catch (Exception ignored) {
            // keep trimmed username
        }
        if (operator.isBlank()) {
            return List.of();
        }
        return listTasksByOperator(operator);
    }

    /**
     * Agent 只读：按任务 ID 查详情。仅发布者或接单员可见，不含联系方式。
     */
    public Map<String, Object> agentTaskDetail(Long taskId, String username) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (taskId == null || taskId <= 0) {
            data.put("error", "请提供有效的任务编号");
            data.put("code", "BAD_REQUEST");
            return data;
        }
        String me = username == null ? "" : username.trim();
        if (me.isBlank()) {
            data.put("error", "username 不能为空");
            data.put("code", "BAD_REQUEST");
            return data;
        }
        DeliveryTask task;
        try {
            task = getTaskById(taskId);
        } catch (NoSuchElementException ex) {
            data.put("error", "找不到任务 #" + taskId);
            data.put("code", "NOT_FOUND");
            return data;
        }
        String pub = task.getPublisherName() == null ? "" : task.getPublisherName().trim();
        String claim = task.getClaimerName() == null ? "" : task.getClaimerName().trim();
        boolean publisher = me.equalsIgnoreCase(pub);
        boolean claimer = !claim.isBlank() && me.equalsIgnoreCase(claim);
        if (!publisher && !claimer) {
            data.put("error", "这不是你相关的任务，无法查看详情");
            data.put("code", "FORBIDDEN");
            return data;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String role = publisher ? "publisher" : "claimer";
        data.put("id", task.getId());
        data.put("title", task.getTitle());
        data.put("status", task.getStatus() == null ? "" : task.getStatus().name());
        data.put("statusLabel", agentStatusLabel(task));
        data.put("reward", task.getReward());
        data.put("role", role);
        data.put("roleLabel", publisher ? "你发布的" : "你接的");
        data.put("pickupLocation", task.getPickupLocation());
        data.put("dropoffLocation", task.getDropoffLocation());
        data.put("claimerName", claim.isBlank() ? "" : claim);
        data.put("publisherName", pub);
        data.put("bountyPrepaid", Boolean.TRUE.equals(task.getBountyPrepaid()));
        data.put("latestDeliveryAt", formatTime(task.getLatestDeliveryAt(), fmt));
        data.put("updatedAt", formatTime(
                task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt(),
                fmt
        ));
        if (task.getAppealAuditedAt() != null) {
            data.put("appealAudited", true);
            data.put("appealApproved", Boolean.TRUE.equals(task.getAppealApproved()));
        } else {
            data.put("appealAudited", false);
        }
        return data;
    }

    /**
     * Agent 只读：任务时间线（含待确认自动确认倒计时、最晚送达等）。
     */
    public Map<String, Object> agentTaskTimeline(Long taskId, String username) {
        Map<String, Object> data = agentTaskDetail(taskId, username);
        if (data.get("error") != null) {
            return data;
        }
        DeliveryTask task;
        try {
            task = getTaskById(taskId);
        } catch (NoSuchElementException ex) {
            data.put("error", "找不到任务 #" + taskId);
            data.put("code", "NOT_FOUND");
            return data;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime now = LocalDateTime.now();
        data.put("createdAt", formatTime(task.getCreatedAt(), fmt));
        data.put("deliveryStartAt", formatTime(task.getDeliveryStartAt(), fmt));
        data.put("deliveryEndAt", formatTime(task.getDeliveryEndAt(), fmt));
        data.put("autoConfirmMinutes", pendingConfirmAutoMinutes);

        TaskStatus status = task.getStatus();
        if (status == TaskStatus.PENDING_CONFIRMATION) {
            LocalDateTime base = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
            if (base != null && pendingConfirmAutoMinutes > 0) {
                LocalDateTime autoAt = base.plusMinutes(pendingConfirmAutoMinutes);
                long remaining = ChronoUnit.MINUTES.between(now, autoAt);
                data.put("autoConfirmAt", formatTime(autoAt, fmt));
                data.put("autoConfirmRemainingMinutes", Math.max(0L, remaining));
                data.put("autoConfirmDue", remaining <= 0);
                data.put("timelineHint", remaining > 0
                        ? ("约 " + remaining + " 分钟后若发布方仍未确认，将自动确认完成")
                        : "已到自动确认时间，系统即将或正在自动确认");
            }
        } else if (status == TaskStatus.CLAIMED && task.getLatestDeliveryAt() != null) {
            long remaining = ChronoUnit.MINUTES.between(now, task.getLatestDeliveryAt());
            data.put("deliveryDeadlineAt", formatTime(task.getLatestDeliveryAt(), fmt));
            data.put("deliveryRemainingMinutes", remaining);
            data.put("deliveryOverdue", remaining < 0);
            data.put("timelineHint", remaining >= 0
                    ? ("距最晚送达约 " + remaining + " 分钟")
                    : ("已超过最晚送达约 " + Math.abs(remaining) + " 分钟"));
        } else if (status == TaskStatus.OVERDUE) {
            data.put("timelineHint", "接单已超时，请查看任务详情或申诉入口");
        } else if (status == TaskStatus.COMPLETED) {
            data.put("timelineHint", "任务已完成");
        } else {
            data.put("timelineHint", "当前状态：" + agentStatusLabel(task));
        }
        return data;
    }

    /**
     * Agent 只读：当前用户已裁决的申诉记录（按裁决时间倒序）。
     */
    public Map<String, Object> agentAppeals(String username, Long userId, Integer limit) {
        String me = username == null ? "" : username.trim();
        int take = (limit == null || limit <= 0) ? 5 : Math.min(8, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        if (me.isBlank()) {
            data.put("error", "username 不能为空");
            data.put("code", "BAD_REQUEST");
            return data;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<DeliveryTask> matched = listTasksForAgent(me, userId).stream()
                .filter(t -> t.getAppealAuditedAt() != null)
                .sorted((a, b) -> b.getAppealAuditedAt().compareTo(a.getAppealAuditedAt()))
                .toList();

        List<Map<String, Object>> items = new ArrayList<>();
        int end = Math.min(matched.size(), take);
        for (int i = 0; i < end; i++) {
            DeliveryTask t = matched.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", t.getId());
            row.put("title", t.getTitle());
            row.put("taskStatus", t.getStatus() == null ? "" : t.getStatus().name());
            row.put("taskStatusLabel", agentStatusLabel(t));
            boolean approved = Boolean.TRUE.equals(t.getAppealApproved());
            row.put("appealApproved", approved);
            row.put("appealStatusLabel", approved ? "申诉通过" : "申诉驳回");
            Boolean serviceCompleted = t.getAppealServiceCompleted();
            if (approved && serviceCompleted != null) {
                row.put("serviceCompleted", serviceCompleted);
                row.put("resultHint", serviceCompleted ? "已按完成结算" : "无责不结算");
            } else {
                row.put("serviceCompleted", null);
                row.put("resultHint", approved ? "" : "维持原状");
            }
            row.put("appealRemark", t.getAppealRemark() == null ? "" : t.getAppealRemark());
            row.put("auditedAt", formatTime(t.getAppealAuditedAt(), fmt));
            items.add(row);
        }
        data.put("username", me);
        data.put("matched", matched.size());
        data.put("shown", items.size());
        data.put("items", items);
        return data;
    }

    /**
     * Agent 只读：本周加急配额（username 解析失败时返回错误，不抛 500）。
     */
    public Map<String, Object> agentUrgentQuota(String username, Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        String me = username == null ? "" : username.trim();
        if (me.isBlank() && userId == null) {
            data.put("error", "username 不能为空");
            data.put("code", "BAD_REQUEST");
            return data;
        }
        try {
            return getUrgentQuota(me.isBlank() ? null : me, userId);
        } catch (Exception ex) {
            data.put("error", "暂时查不到加急配额：" + (ex.getMessage() == null ? "请稍后再试" : ex.getMessage()));
            data.put("code", "UNAVAILABLE");
            return data;
        }
    }

    private static String formatTime(LocalDateTime time, DateTimeFormatter fmt) {
        return time == null ? "" : time.format(fmt);
    }

    private static String agentStatusLabel(DeliveryTask task) {
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.PENDING_REVIEW && !Boolean.TRUE.equals(task.getBountyPrepaid())) {
            return "待支付赏金";
        }
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING_REVIEW -> "待管理员审核";
            case REJECTED -> "审核驳回";
            case OPEN -> "待接单";
            case CLAIMED -> "配送中";
            case OVERDUE -> "接单员超时";
            case PENDING_CONFIRMATION -> "待发布方确认";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }

    private List<DeliveryTask> listTasksByOperator(String operator) {
        Map<Long, DeliveryTask> merged = new LinkedHashMap<>();
        for (DeliveryTask task : deliveryTaskRepository.findTaskLinksForUser(operator)) {
            if (task.getId() != null) {
                merged.put(task.getId(), task);
            }
        }
        for (DeliveryTask draft : deliveryTaskRepository.findByPublisherNameIgnoreCaseAndStatusAndBountyPrepaid(
                operator, TaskStatus.PENDING_REVIEW, false)) {
            if (draft.getId() != null) {
                merged.putIfAbsent(draft.getId(), draft);
            }
        }
        return merged.values().stream()
                .sorted((a, b) -> {
                    var ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    var tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                    if (ta == null && tb == null) {
                        return Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId());
                    }
                    if (ta == null) {
                        return 1;
                    }
                    if (tb == null) {
                        return -1;
                    }
                    return tb.compareTo(ta);
                })
                .toList();
    }

    public DeliveryTask createTask(TaskCreateRequest request, String currentUser) {
        validateCreateRequest(request);
        String operator = requireOperatorName(currentUser);
        validateUrgentForCreate(request, operator);

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if ("WALLET".equals(paymentMethod)) {
            cancelUnpaidDraftsForPublisher(operator);
            DeliveryTask saved = self.getObject().createTaskWithWalletPayment(request, operator);
            return self.getObject().finalizeWalletPaidTask(saved);
        }

        if (deliveryTaskRepository.existsByPublisherNameIgnoreCaseAndStatusAndBountyPrepaid(
                operator, TaskStatus.PENDING_REVIEW, false)) {
            throw new IllegalStateException("您还有未支付赏金的待审核任务，请先完成支付或等待其自动取消后再发布新任务");
        }
        return self.getObject().createTaskWithLocalPayment(request, operator);
    }

    /** 支付宝/微信：仅本地写库，不开启 Seata 全局事务 */
    @Transactional
    public DeliveryTask createTaskWithLocalPayment(TaskCreateRequest request, String operator) {
        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if ("WECHAT".equals(paymentMethod)) {
            throw new IllegalStateException("微信支付暂未开发");
        }
        DeliveryTask task = new DeliveryTask();
        populateTaskFromCreateRequest(task, request, operator);
        task.setStatus(TaskStatus.PENDING_REVIEW);
        task.setBountyPrepaid(false);
        if (request.getUserCouponId() != null) {
            couponCore.preview(operator, request.getUserCouponId(), task.getReward());
            task.setUserCouponId(request.getUserCouponId());
        }
        DeliveryTask saved = saveTask(task);
        notifyTaskSubmittedIfPending(saved);
        return saved;
    }

    /**
     * 钱包支付：任务写库 + Feign 调 wallet 锁定赏金。
     * Seata 全局事务：wallet 扣款失败则任务写入一并回滚（需 seata-server:8091 与 campus.remote.wallet=true）。
     */
    @GlobalTransactional(name = "campus-create-task-wallet", rollbackFor = Exception.class, timeoutMills = 60000)
    @Transactional
    public DeliveryTask createTaskWithWalletPayment(TaskCreateRequest request, String operator) {
        DeliveryTask task = new DeliveryTask();
        populateTaskFromCreateRequest(task, request, operator);
        task.setStatus(TaskStatus.PENDING_REVIEW);
        task.setBountyPrepaid(false);
        DeliveryTask saved = deliveryTaskRepository.save(task);
        CouponQuote quote = couponCore.consume(operator, request.getUserCouponId(), saved.getId(), saved.getReward());
        applyCouponQuote(saved, quote, request.getUserCouponId());
        saved = deliveryTaskRepository.save(saved);
        BigDecimal payable = resolvePayable(saved);
        walletSupport.lockTaskBounty(operator, payable, saved.getId(), saved.getTitle());
        saved.setBountyPrepaid(true);
        stampUrgentAt(saved);
        return deliveryTaskRepository.save(saved);
    }

    /** 全局事务提交后的本地收尾：ES 同步、自动审核、系统消息等不参与 Seata */
    @Transactional
    public DeliveryTask finalizeWalletPaidTask(DeliveryTask saved) {
        saved = autoApproveAfterBountyIfEligible(saved, "system");
        saved = saveTask(saved);
        notifyTaskSubmittedIfPending(saved);
        return saved;
    }

    private void populateTaskFromCreateRequest(DeliveryTask task, TaskCreateRequest request, String operator) {
        task.setTaskType(normalizeTaskTypeForSave(request.getTaskType()));
        task.setTitle(request.getTitle().trim());
        task.setPickupLocation(request.getPickupLocation().trim());
        task.setPickupLat(request.getPickupLat());
        task.setPickupLng(request.getPickupLng());
        task.setDropoffLocation(request.getDropoffLocation().trim());
        task.setDropoffLat(request.getDropoffLat());
        task.setDropoffLng(request.getDropoffLng());
        task.setReward(request.getReward());
        task.setPublisherName(operator);
        task.setContact(request.getContact().trim());
        task.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        task.setDeliveryStartAt(request.getDeliveryStartAt());
        task.setDeliveryEndAt(request.getDeliveryEndAt());
        task.setLatestDeliveryAt(request.getDeliveryEndAt() != null ? request.getDeliveryEndAt() : request.getLatestDeliveryAt());
        task.setImageUrls(toJsonImageUrls(request.getImageUrls()));
        applyUrgentFields(task, request);
    }

    private void applyUrgentFields(DeliveryTask task, TaskCreateRequest request) {
        if (!Boolean.TRUE.equals(request.getUrgentEnabled())) {
            task.setUrgentEnabled(false);
            task.setUrgentFee(null);
            task.setUrgentRiderBonus(null);
            task.setUrgentPushFee(null);
            task.setUrgentAt(null);
            return;
        }
        BigDecimal fee = normalizeUrgentFee(request.getUrgentFee());
        BigDecimal riderBonus = fee.multiply(URGENT_RIDER_RATIO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pushFee = fee.subtract(riderBonus).setScale(2, RoundingMode.HALF_UP);
        task.setUrgentEnabled(true);
        task.setUrgentFee(fee);
        task.setUrgentRiderBonus(riderBonus);
        task.setUrgentPushFee(pushFee);
        // urgentAt 在赏金预付成功后写入
    }

    private void stampUrgentAt(DeliveryTask task) {
        if (task != null && Boolean.TRUE.equals(task.getUrgentEnabled()) && task.getUrgentAt() == null) {
            task.setUrgentAt(LocalDateTime.now());
        }
    }

    private void validateUrgentForCreate(TaskCreateRequest request, String operator) {
        if (!Boolean.TRUE.equals(request.getUrgentEnabled())) {
            return;
        }
        normalizeUrgentFee(request.getUrgentFee());
        LocalDateTime weekStart = currentWeekStart();
        long used = deliveryTaskRepository.countUrgentUsedThisWeek(operator, weekStart);
        if (used >= URGENT_WEEKLY_LIMIT) {
            throw new IllegalStateException("本周加急次数已用完（每周最多 " + URGENT_WEEKLY_LIMIT + " 次）");
        }
    }

    private BigDecimal normalizeUrgentFee(BigDecimal raw) {
        if (raw == null || raw.signum() <= 0) {
            throw new IllegalArgumentException("请填写加急金额");
        }
        BigDecimal fee = raw.setScale(2, RoundingMode.HALF_UP);
        if (fee.compareTo(URGENT_FEE_MIN) < 0 || fee.compareTo(URGENT_FEE_MAX) > 0) {
            throw new IllegalArgumentException("加急金额需在 ¥" + URGENT_FEE_MIN.toPlainString()
                    + "～¥" + URGENT_FEE_MAX.toPlainString() + " 之间");
        }
        BigDecimal steps = fee.divide(URGENT_STEP, 4, RoundingMode.HALF_UP);
        if (steps.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("加急金额需以 " + URGENT_STEP.toPlainString() + " 元为步进");
        }
        return fee;
    }

    private LocalDateTime currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    private BigDecimal resolveClaimerIncome(DeliveryTask task) {
        BigDecimal base = task.getReward() == null ? BigDecimal.ZERO : task.getReward();
        BigDecimal bonus = task.getUrgentRiderBonus() == null ? BigDecimal.ZERO : task.getUrgentRiderBonus();
        return base.add(bonus).setScale(2, RoundingMode.HALF_UP);
    }

    private void notifyTaskSubmittedIfPending(DeliveryTask saved) {
        if (saved.getStatus() == TaskStatus.PENDING_REVIEW) {
            RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
            if (systemMessages != null) {
                systemMessages.notifyTaskSubmitted(saved);
            }
        }
    }

    public Map<String, Object> getAlipayBountyInfo(Long taskId, String username) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        String user = requireOperatorName(username);
        DeliveryTask task = requirePayableAlipayTask(taskId, user);
        return alipayBountySnapshot(task);
    }

    @Transactional
    public Map<String, Object> prepareAlipayBountyPayment(Long taskId, Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String username = body.get("username") == null ? "" : String.valueOf(body.get("username")).trim();
        String user = requireOperatorName(username);
        DeliveryTask task = requirePayableAlipayTask(taskId, user);

        Long userCouponId = parseLongOrNull(body.get("userCouponId"));
        if (userCouponId == null) {
            userCouponId = task.getUserCouponId();
        }

        if (userCouponId != null) {
            CouponQuote quote = couponCore.lock(user, userCouponId, task.getId(), task.getReward());
            applyCouponQuote(task, quote, userCouponId);
        } else {
            task.setUserCouponId(null);
            task.setCouponDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            task.setBountyPayable(task.getReward());
        }
        DeliveryTask saved = saveTask(task);
        return alipayBountySnapshot(saved);
    }

    @Transactional
    public void confirmAlipayBountyPayment(Long taskId, Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String username = body.get("username") == null ? "" : String.valueOf(body.get("username")).trim();
        String outTradeNo = body.get("outTradeNo") == null ? "" : String.valueOf(body.get("outTradeNo")).trim();
        if (outTradeNo.isBlank()) {
            throw new IllegalArgumentException("支付宝订单号不能为空");
        }
        String user = requireOperatorName(username);
        DeliveryTask task = getTaskById(taskId);
        if (task.getPublisherName() == null || !task.getPublisherName().equalsIgnoreCase(user)) {
            throw new IllegalStateException("无权确认该任务赏金");
        }
        if (Boolean.TRUE.equals(task.getBountyPrepaid())) {
            return;
        }
        BigDecimal payable = resolvePayable(task);
        if (task.getStatus() == TaskStatus.REJECTED || task.getStatus() == TaskStatus.CANCELLED) {
            // 支付回调晚于驳回/取消：直接原路退款，避免赏金悬空
            walletSupport.refundTaskBounty(user, taskId, payable, "任务已关闭");
            restoreTaskCoupon(task);
            return;
        }
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            throw new IllegalStateException("当前任务状态不可确认赏金");
        }
        couponCore.confirmLocked(task.getUserCouponId(), task.getId());
        task.setBountyPrepaid(true);
        stampUrgentAt(task);
        DeliveryTask saved = saveTask(task);
        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null && payable != null) {
            systemMessages.notifyBountyPaid(user, payable, taskId, task.getTitle());
        }
        autoApproveAfterBountyIfEligible(saved, user);
    }

    @Transactional
    public DeliveryTask claimTask(Long id, String claimerName, Long claimerUserId) {
        markOverdueTasks();

        UserAccount claimer = resolveUserAccount(claimerName, claimerUserId);
        String normalizedClaimerName = claimer.getUsername();

        if (!claimer.isRiderApproved()) {
            throw new IllegalStateException("你还不是已认证接单员，请先在发布页提交接单员申请");
        }
        LocalDateTime now = LocalDateTime.now();
        int credit = CreditRules.effectiveScore(claimer.getCreditScore());
        if (CreditRules.isBlocked(claimer.getBlockedUntil(), now)) {
            throw new IllegalStateException(
                    "当前处于禁接期，暂时不能接单。解禁时间：" + claimer.getBlockedUntil()
                            + "（当前信誉分：" + credit + "）"
            );
        }
        if (credit < CreditRules.MIN_TO_CLAIM) {
            throw new IllegalStateException("信誉分过低，当前为 " + credit + "，暂不可接单（需 ≥ " + CreditRules.MIN_TO_CLAIM + "）");
        }

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new IllegalStateException("该任务不可接单，当前状态: " + task.getStatus());
        }
        if (task.getPublisherName() != null && task.getPublisherName().equalsIgnoreCase(normalizedClaimerName)) {
            throw new IllegalStateException("不能接取自己发布的任务");
        }
        task.setStatus(TaskStatus.CLAIMED);
        task.setClaimerName(normalizedClaimerName);

        DeliveryTask saved = saveTask(task);
        pushTaskStatusChanged(saved, "TASK_CLAIMED");
        return saved;
    }

    @Transactional
    public DeliveryTask completeTask(Long id, TaskCompleteRequest request, String operatorName) {
        markOverdueTasks();
        String operator = requireOperatorName(operatorName);

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.CLAIMED) {
            throw new IllegalStateException("只有已接单任务才能提交完成");
        }
        if (task.getClaimerName() == null || !task.getClaimerName().equalsIgnoreCase(operator)) {
            throw new IllegalStateException("只有接单人本人才能提交完成");
        }

        String note = request != null && request.getNote() != null ? request.getNote().trim() : "";
        if (note.length() > 500) {
            throw new IllegalArgumentException("完成留言最多 500 字");
        }
        List<String> proofImages = request != null ? request.getProofImages() : null;
        String proofJson = toJsonImageUrls(proofImages);

        task.setCompletionNote(note.isBlank() ? null : note);
        task.setCompletionProofImages("[]".equals(proofJson) ? null : proofJson);
        task.setStatus(TaskStatus.PENDING_CONFIRMATION);
        DeliveryTask saved = saveTask(task);
        pushTaskStatusChanged(saved, "TASK_PENDING_CONFIRMATION");
        return saved;
    }

    public DeliveryTask cancelTask(Long id, String operatorName) {
        markOverdueTasks();
        String operator = requireOperatorName(operatorName);

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("已完成任务不可取消");
        }

        boolean isPublisher = task.getPublisherName() != null && task.getPublisherName().equalsIgnoreCase(operator);
        boolean isClaimer = task.getClaimerName() != null && task.getClaimerName().equalsIgnoreCase(operator);

        if (!isPublisher && !isClaimer) {
            throw new IllegalStateException("只有发布人或接单人可取消该任务");
        }

        if (task.getStatus() == TaskStatus.PENDING_CONFIRMATION && isPublisher) {
            throw new IllegalStateException(
                    "接单员已提交完成，请「确认完成」以结算赏金；如有争议请通过私聊沟通或向管理员申诉，不可在此阶段单方取消任务");
        }

        if ((task.getStatus() == TaskStatus.CLAIMED || task.getStatus() == TaskStatus.PENDING_CONFIRMATION) && isClaimer && !isPublisher) {
            return self.getObject().reopenClaimedTask(id);
        }

        if (shouldRefundPrepaidBounty(task)) {
            return self.getObject().cancelTaskAndRefundBounty(id);
        }
        return self.getObject().cancelTaskLocally(id);
    }

    @Transactional
    public DeliveryTask reopenClaimedTask(Long id) {
        DeliveryTask task = getTaskById(id);
        task.setStatus(TaskStatus.OPEN);
        task.setClaimerName(null);
        task.setCompletionNote(null);
        task.setCompletionProofImages(null);
        DeliveryTask saved = saveTask(task);
        pushTaskStatusChanged(saved, "TASK_REOPENED");
        return saved;
    }

    /** 未冻结赏金（如待审核未支付）：仅本地取消，不依赖 Seata */
    @Transactional
    public DeliveryTask cancelTaskLocally(Long id) {
        DeliveryTask task = getTaskById(id);
        restoreTaskCoupon(task);
        task.setStatus(TaskStatus.CANCELLED);
        DeliveryTask saved = saveTask(task);
        pushTaskStatusChanged(saved, "TASK_CANCELLED");
        return saved;
    }

    /** 已付赏金：Feign 同步退款 + 本地取消（不启全局事务，避免 Seata 未就绪时无法取消） */
    @Transactional
    public DeliveryTask cancelTaskAndRefundBounty(Long id) {
        DeliveryTask task = getTaskById(id);
        walletSupport.refundTaskBounty(
                task.getPublisherName(),
                task.getId(),
                resolvePayable(task),
                "任务已取消"
        );
        restoreTaskCoupon(task);
        task.setStatus(TaskStatus.CANCELLED);
        task.setBountyPrepaid(false);
        DeliveryTask saved = saveTask(task);
        pushTaskStatusChanged(saved, "TASK_CANCELLED");
        return saved;
    }

    /** 发布者确认完成：Feign 同步入账 + 本地更新（不启全局事务，避免 Seata 未就绪时卡住） */
    @Transactional
    public DeliveryTask confirmTask(Long id, String operatorName) {
        String operator = requireOperatorName(operatorName);
        markOverdueTasks();

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return task;
        }
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("只有待发送方确认的任务才能确认完成");
        }
        if (task.getPublisherName() == null || !task.getPublisherName().equalsIgnoreCase(operator)) {
            throw new IllegalStateException("只有发布人本人才能确认完成");
        }

        return settlePendingConfirmationTask(task, "TASK_CONFIRMED");
    }

    /**
     * 管理员申诉裁决：
     * - 通过且服务完成：任务置为 COMPLETED，并给接单员入账（幂等）
     * - 通过但服务未完成：不结算，但标记为接单员无责（不扣完成率）
     * - 驳回：不改任务状态/结算，仅记录裁决信息
     * 不启 Seata 全局事务（与 cancelTaskAndRefundBounty 一致），避免 TC 未连上 MySQL 时无法裁决。
     */
    @Transactional
    public DeliveryTask auditAppeal(Long id, boolean approve, boolean serviceCompleted, String remark, String operatorName) {
        String operator = requireOperatorName(operatorName);
        requireAdmin(operator);

        DeliveryTask task = getTaskById(id);
        String safeRemark = remark == null ? "" : remark.trim();

        task.setAppealApproved(approve);
        task.setAppealServiceCompleted(approve ? serviceCompleted : null);
        task.setAppealAuditedBy(operator);
        task.setAppealRemark(safeRemark);
        task.setAppealAuditedAt(LocalDateTime.now());

        if (approve) {
            // 申诉成立：对接单员不扣完成率
            task.setRiderNoFault(true);

            if (serviceCompleted) {
                // 认定服务完成：任务按完成结算
                task.setStatus(TaskStatus.COMPLETED);
                DeliveryTask saved = saveTask(task);
                if (saved.getClaimerName() != null && saved.getReward() != null) {
                    walletSupport.incomeFromTask(saved.getClaimerName(), resolveClaimerIncome(saved), saved.getId(), saved.getTitle());
                    recordCouponSubsidyIfNeeded(saved);
                }
                if (Boolean.TRUE.equals(saved.getBountyPrepaid())) {
                    saved.setBountyPrepaid(false);
                    saved = saveTask(saved);
                }
                remoteAuditLog.log(operator, "APPEAL_APPROVED", id, "申诉通过并结算：" + saved.getTitle());
                pushTaskStatusChanged(saved, "APPEAL_APPROVED");
                return saved;
            }

            // 服务未完成：维持原状态（通常是 CANCELLED/REJECTED），但标记无责
            DeliveryTask saved = saveTask(task);
            remoteAuditLog.log(operator, "APPEAL_APPROVED_NO_PAY", id, "申诉通过（无责不结算）：" + saved.getTitle());
            pushTaskStatusChanged(saved, "APPEAL_APPROVED_NO_PAY");
            return saved;
        }

        // 驳回：记录裁决信息
        DeliveryTask saved = saveTask(task);
        remoteAuditLog.log(operator, "APPEAL_REJECTED", id, "申诉驳回：" + saved.getTitle());
        pushTaskStatusChanged(saved, "APPEAL_REJECTED");
        return saved;
    }

    @Transactional
    public DeliveryTask approveTask(Long id, String operatorName) {
        String operator = requireOperatorName(operatorName);
        requireAdmin(operator);

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只有待审核任务可通过审核");
        }
        if (!Boolean.TRUE.equals(task.getBountyPrepaid())) {
            throw new IllegalStateException("任务赏金尚未支付，无法审核通过");
        }

        task.setStatus(TaskStatus.OPEN);
        task.setAdminRejectRemark(null);
        DeliveryTask saved = saveTask(task);
        remoteAuditLog.log(operator, "TASK_APPROVED", id, "审核通过任务：" + saved.getTitle());
        pushTaskStatusChanged(saved, "TASK_APPROVED");
        return saved;
    }

    @Transactional
    public DeliveryTask rejectTask(Long id, String operatorName) {
        return rejectTask(id, operatorName, "任务内容疑似包含辱骂/色情等不良信息，无法通过审核。请修改后重新发布/提交。");
    }

    @Transactional
    public DeliveryTask rejectTask(Long id, String operatorName, String remark) {
        String operator = requireOperatorName(operatorName);
        requireAdmin(operator);

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只有待审核任务可驳回");
        }

        String safeRemark = remark == null ? "" : remark.trim();
        String finalRemark =
                safeRemark.isBlank() ? "任务内容疑似包含不良信息，无法通过审核。请修改后重新发布/提交。" : safeRemark;
        if (finalRemark.length() > 500) {
            finalRemark = finalRemark.substring(0, 500);
        }
        task.setAdminRejectRemark(finalRemark);
        if (shouldRefundPrepaidBounty(task)) {
            walletSupport.refundTaskBounty(
                    task.getPublisherName(),
                    task.getId(),
                    resolvePayable(task),
                    "管理员驳回任务"
            );
            task.setBountyPrepaid(false);
        }
        restoreTaskCoupon(task);
        task.setStatus(TaskStatus.REJECTED);
        DeliveryTask saved = saveTask(task);
        remoteAuditLog.log(operator, "TASK_REJECTED", id, "驳回任务：" + saved.getTitle() + "；原因：" + saved.getAdminRejectRemark());
        pushTaskStatusChanged(saved, "TASK_REJECTED");
        return saved;
    }

    public DeliveryTask resubmitRejectedTask(Long id, TaskCreateRequest request, String operatorName) {
        validateCreateRequest(request);
        String operator = requireOperatorName(operatorName);
        validateUrgentForCreate(request, operator);

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.REJECTED) {
            throw new IllegalStateException("只有被驳回任务可修改后重新提交");
        }
        if (task.getPublisherName() == null || !task.getPublisherName().equalsIgnoreCase(operator)) {
            throw new IllegalStateException("只有发布者本人可修改并重新提交");
        }

        populateTaskFromCreateRequest(task, request, operator);
        task.setBountyPrepaid(false);
        task.setStatus(TaskStatus.PENDING_REVIEW);
        task.setClaimerName(null);
        task.setClaimerRating(null);
        task.setClaimerRatingComment(null);
        task.setClaimerRatedAt(null);
        task.setOverdueRejected(false);
        task.setAdminRejectRemark(null);
        task.setUserCouponId(request.getUserCouponId());
        task.setCouponDiscount(null);
        task.setBountyPayable(null);
        if (request.getUserCouponId() != null) {
            couponCore.preview(operator, request.getUserCouponId(), task.getReward());
        }

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if ("WALLET".equals(paymentMethod)) {
            DeliveryTask saved = self.getObject().resubmitRejectedTaskWithWalletPayment(task, operator, id);
            return self.getObject().finalizeResubmittedWalletTask(saved, operator, id);
        }
        return self.getObject().resubmitRejectedTaskWithLocalPayment(task, operator, id);
    }

    @Transactional
    public DeliveryTask resubmitRejectedTaskWithLocalPayment(DeliveryTask task, String operator, Long id) {
        DeliveryTask saved = saveTask(task);
        remoteAuditLog.log(operator, "TASK_RESUBMITTED", id, "发布者重新提交任务：" + saved.getTitle());
        pushTaskStatusChanged(saved, "TASK_RESUBMITTED");
        return saved;
    }

    @GlobalTransactional(name = "campus-resubmit-task-wallet", rollbackFor = Exception.class, timeoutMills = 60000)
    @Transactional
    public DeliveryTask resubmitRejectedTaskWithWalletPayment(DeliveryTask task, String operator, Long id) {
        DeliveryTask saved = deliveryTaskRepository.save(task);
        CouponQuote quote = couponCore.consume(operator, saved.getUserCouponId(), saved.getId(), saved.getReward());
        applyCouponQuote(saved, quote, saved.getUserCouponId());
        saved = deliveryTaskRepository.save(saved);
        walletSupport.lockTaskBounty(operator, resolvePayable(saved), saved.getId(), saved.getTitle());
        saved.setBountyPrepaid(true);
        stampUrgentAt(saved);
        return deliveryTaskRepository.save(saved);
    }

    @Transactional
    public DeliveryTask finalizeResubmittedWalletTask(DeliveryTask saved, String operator, Long id) {
        saved = autoApproveAfterBountyIfEligible(saved, "system");
        saved = saveTask(saved);
        notifyTaskSubmittedIfPending(saved);
        remoteAuditLog.log(operator, "TASK_RESUBMITTED", id, "发布者重新提交任务：" + saved.getTitle());
        pushTaskStatusChanged(saved, "TASK_RESUBMITTED");
        return saved;
    }

    private String normalizePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALIPAY";
        }
        String method = raw.trim().toUpperCase();
        if ("ALIPAY".equals(method) || "WECHAT".equals(method) || "WALLET".equals(method)) {
            return method;
        }
        throw new IllegalArgumentException("不支持的支付方式：" + raw);
    }

    /** 钱包发布前清理未付赏金的草稿（仅本地取消，不触发钱包/Seata） */
    private void cancelUnpaidDraftsForPublisher(String operator) {
        List<DeliveryTask> drafts = deliveryTaskRepository.findByPublisherNameIgnoreCaseAndStatusAndBountyPrepaid(
                operator, TaskStatus.PENDING_REVIEW, false);
        for (DeliveryTask draft : drafts) {
            if (draft.getId() == null) {
                continue;
            }
            try {
                restoreTaskCoupon(draft);
                draft.setStatus(TaskStatus.CANCELLED);
                deliveryTaskRepository.save(draft);
                taskElasticsearchService.ifPresent(es -> es.sync(draft));
            } catch (Exception ignore) {
                // 单条失败不影响后续
            }
        }
    }

    private DeliveryTask requirePayableAlipayTask(Long taskId, String user) {
        DeliveryTask task = getTaskById(taskId);
        if (task.getPublisherName() == null || !task.getPublisherName().equalsIgnoreCase(user)) {
            throw new IllegalStateException("无权支付该任务赏金");
        }
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            throw new IllegalStateException("当前任务状态不可支付赏金");
        }
        if (Boolean.TRUE.equals(task.getBountyPrepaid())) {
            throw new IllegalStateException("任务赏金已支付");
        }
        if (task.getReward() == null || task.getReward().signum() <= 0) {
            throw new IllegalArgumentException("任务赏金无效");
        }
        return task;
    }

    private Map<String, Object> alipayBountySnapshot(DeliveryTask task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("title", task.getTitle());
        out.put("amount", resolvePayable(task));
        out.put("reward", task.getReward());
        out.put("discount", task.getCouponDiscount() == null ? BigDecimal.ZERO : task.getCouponDiscount());
        out.put("publisherName", task.getPublisherName());
        out.put("userCouponId", task.getUserCouponId());
        return out;
    }

    private BigDecimal resolvePayable(DeliveryTask task) {
        if (task == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal bountyPart;
        if (task.getBountyPayable() != null && task.getBountyPayable().signum() > 0) {
            bountyPart = task.getBountyPayable();
        } else {
            bountyPart = task.getReward() == null ? BigDecimal.ZERO : task.getReward();
        }
        BigDecimal urgent = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(task.getUrgentEnabled()) && task.getUrgentFee() != null) {
            urgent = task.getUrgentFee();
        }
        return bountyPart.add(urgent).setScale(2, RoundingMode.HALF_UP);
    }

    private void applyCouponQuote(DeliveryTask task, CouponQuote quote, Long userCouponId) {
        if (quote == null) {
            return;
        }
        task.setUserCouponId(userCouponId);
        task.setCouponDiscount(quote.discount());
        task.setBountyPayable(quote.payable());
    }

    private void restoreTaskCoupon(DeliveryTask task) {
        if (task == null || task.getUserCouponId() == null) {
            return;
        }
        try {
            couponCore.restore(task.getUserCouponId(), task.getId());
        } catch (Exception ignore) {
            // 还券失败不阻断主流程
        }
    }

    private void recordCouponSubsidyIfNeeded(DeliveryTask task) {
        if (task == null || task.getCouponDiscount() == null || task.getCouponDiscount().signum() <= 0) {
            return;
        }
        walletSupport.recordCouponSubsidy(
                task.getPublisherName(),
                task.getId(),
                task.getCouponDiscount(),
                task.getTitle()
        );
    }

    private Long parseLongOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Long.parseLong(text);
    }

    private void pushTaskStatusChanged(DeliveryTask task, String eventType) {
        taskNotification.notifyTaskStatusChanged(task, eventType);
    }

    @Transactional
    public void deleteTask(Long id, String operatorName) {
        String operator = requireOperatorName(operatorName);
        DeliveryTask task = getTaskById(id);

        boolean isPublisher = task.getPublisherName() != null && task.getPublisherName().equalsIgnoreCase(operator);
        boolean isClaimer = task.getClaimerName() != null && task.getClaimerName().equalsIgnoreCase(operator);
        if (!isPublisher && !isClaimer) {
            throw new IllegalStateException("只有任务相关人才能删除该任务");
        }

        if (task.getStatus() == TaskStatus.PENDING_REVIEW || task.getStatus() == TaskStatus.OPEN || task.getStatus() == TaskStatus.CLAIMED || task.getStatus() == TaskStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("进行中的任务不可删除");
        }

        if (shouldRefundPrepaidBounty(task) && (task.getStatus() == TaskStatus.REJECTED || task.getStatus() == TaskStatus.CANCELLED)) {
            walletSupport.refundTaskBounty(
                    task.getPublisherName(),
                    task.getId(),
                    resolvePayable(task),
                    "任务已删除"
            );
        }
        restoreTaskCoupon(task);

        deleteTaskRecord(task);
        remoteAuditLog.log(operator, "TASK_DELETED", id, "删除任务：" + task.getTitle());
    }

    @Transactional
    public DeliveryTask rateClaimer(Long id, TaskRateRequest request, String operatorName) {
        String operator = requireOperatorName(operatorName);

        if (request == null || request.getRating() == null) {
            throw new IllegalArgumentException("评分不能为空");
        }

        double rating = request.getRating();
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分范围必须是1-5分");
        }
        double doubled = rating * 2;
        if (Math.abs(doubled - Math.round(doubled)) > 1e-9) {
            throw new IllegalArgumentException("评分仅支持 0.5 分步进（如 4.5）");
        }

        DeliveryTask task = getTaskById(id);
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成任务才能评分");
        }
        if (task.getPublisherName() == null || !task.getPublisherName().equalsIgnoreCase(operator)) {
            throw new IllegalStateException("只有发布人可以给接单员评分");
        }
        if (task.getClaimerName() == null || task.getClaimerName().isBlank()) {
            throw new IllegalStateException("该任务没有接单员，无法评分");
        }
        if (task.getClaimerRating() != null) {
            throw new IllegalStateException("该任务已评分，不能重复评分");
        }

        String comment = request.getComment() == null ? "" : request.getComment().trim();
        if (comment.length() > 300) {
            throw new IllegalArgumentException("评价内容不能超过300字");
        }

        task.setClaimerRating(java.math.BigDecimal.valueOf(rating).setScale(1, java.math.RoundingMode.HALF_UP));
        task.setClaimerRatingComment(comment);
        task.setClaimerRatedAt(java.time.LocalDateTime.now());
        return saveTask(task);
    }

    @Transactional
    public void seedDemoData() {
        if (deliveryTaskRepository.count() > 0) {
            return;
        }

        List<TaskCreateRequest> examples = new ArrayList<>();

        TaskCreateRequest r1 = new TaskCreateRequest();
        r1.setTitle("代取快递");
        r1.setPickupLocation("菜鸟驿站南门店");
        r1.setDropoffLocation("三号宿舍楼 308");
        r1.setReward(new java.math.BigDecimal("4.00"));
        r1.setPublisherName("小李");
        r1.setContact("188****5678");
        r1.setDescription("一个中号快递，今晚8点前送到");
        r1.setLatestDeliveryAt(LocalDateTime.now().plusHours(4));
        r1.setPaymentMethod("WALLET");
        examples.add(r1);

        TaskCreateRequest r2 = new TaskCreateRequest();
        r2.setTitle("代拿外卖");
        r2.setPickupLocation("东门外卖柜");
        r2.setDropoffLocation("一教 A 区门口");
        r2.setReward(new java.math.BigDecimal("3.50"));
        r2.setPublisherName("阿陈");
        r2.setContact("微信: chen123");
        r2.setDescription("正在上课，麻烦11:50前送到");
        r2.setLatestDeliveryAt(LocalDateTime.now().plusHours(2));
        r2.setPaymentMethod("WALLET");
        examples.add(r2);

        walletSupport.creditManualForDemo(r1.getPublisherName(), new java.math.BigDecimal("500.00"));
        walletSupport.creditManualForDemo(r2.getPublisherName(), new java.math.BigDecimal("500.00"));
        examples.forEach(req -> createTask(req, req.getPublisherName()));
    }

    private DeliveryTask getTaskById(Long id) {
        DeliveryTask task = deliveryTaskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("任务不存在，id=" + id));
        return refreshTaskOverdue(task);
    }

    /**
     * 发布者查询本单接单员电话（优先接单员申请手机号，否则注册手机号）。
     */
    public Map<String, String> getClaimerPhoneForPublisher(Long taskId, String currentUser, Long currentUserId) {
        UserAccount publisher = resolveUserAccount(currentUser, currentUserId);
        DeliveryTask task = getTaskById(taskId);

        if (task.getPublisherName() == null
                || !task.getPublisherName().equalsIgnoreCase(publisher.getUsername())) {
            throw new IllegalStateException("只有发布者可以拨打接单员电话");
        }
        if (task.getClaimerName() == null || task.getClaimerName().isBlank()) {
            throw new IllegalStateException("当前还没有接单员");
        }
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.OPEN
                || status == TaskStatus.CANCELLED
                || status == TaskStatus.REJECTED
                || status == TaskStatus.PENDING_REVIEW) {
            throw new IllegalStateException("当前任务状态不可拨打接单员电话");
        }

        UserAccount claimer = userAccountRepository.findByUsernameIgnoreCase(task.getClaimerName().trim())
                .orElseThrow(() -> new IllegalStateException("接单员账号不存在"));

        String phone = firstValidMobile(claimer.getRiderPhone(), claimer.getPhone());
        if (phone == null) {
            throw new IllegalStateException("接单员未填写可用手机号");
        }
        return Map.of("phone", phone);
    }

    private static String firstValidMobile(String... candidates) {
        if (candidates == null) return null;
        for (String raw : candidates) {
            if (raw == null) continue;
            String digits = raw.replaceAll("\\D", "");
            if (digits.length() == 11 && digits.startsWith("1")) {
                return digits;
            }
            if (digits.length() > 11 && digits.startsWith("86")) {
                String local = digits.substring(2);
                if (local.length() == 11 && local.startsWith("1")) {
                    return local;
                }
            }
        }
        return null;
    }

    private String requireOperatorName(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return operatorName.trim();
    }

    public String resolveOperatorUsername(String operatorName, Long operatorUserId) {
        return resolveUserAccount(operatorName, operatorUserId).getUsername();
    }

    private UserAccount resolveUserAccount(String operatorName, Long operatorUserId) {
        if (operatorUserId != null) {
            Optional<UserAccount> byId = userAccountRepository.findById(operatorUserId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("用户不存在");
        }
        String normalized = operatorName.trim();
        return userAccountRepository.findByUsernameIgnoreCase(normalized)
                .or(() -> userAccountRepository.findByUsername(normalized))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private void requireAdmin(String operator) {
        if (!adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可执行该操作");
        }
    }

    private void validateCreateRequest(TaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("任务标题不能为空");
        }

        normalizeTaskTypeForSave(request.getTaskType());

        if (request.getPickupLocation() == null || request.getPickupLocation().isBlank()) {
            throw new IllegalArgumentException("取件地点不能为空");
        }

        if (request.getDropoffLocation() == null || request.getDropoffLocation().isBlank()) {
            throw new IllegalArgumentException("送达地点不能为空");
        }

        if (request.getReward() == null || request.getReward().signum() <= 0) {
            throw new IllegalArgumentException("赏金必须大于0");
        }

        if (request.getContact() == null || request.getContact().isBlank()) {
            throw new IllegalArgumentException("联系方式不能为空");
        }

        if (request.getDeliveryStartAt() == null && request.getLatestDeliveryAt() == null) {
            throw new IllegalArgumentException("配送开始时间不能为空");
        }
        if (request.getDeliveryEndAt() == null && request.getLatestDeliveryAt() == null) {
            throw new IllegalArgumentException("配送结束时间不能为空");
        }
        LocalDateTime startAt = request.getDeliveryStartAt();
        LocalDateTime endAt = request.getDeliveryEndAt() != null ? request.getDeliveryEndAt() : request.getLatestDeliveryAt();
        if (startAt == null) {
            startAt = endAt.minusMinutes(30);
            request.setDeliveryStartAt(startAt);
        }
        if (!endAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("配送结束时间必须晚于当前时间");
        }
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("配送结束时间必须晚于开始时间");
        }
        if (request.getImageUrls() != null && request.getImageUrls().size() > 6) {
            throw new IllegalArgumentException("最多上传 6 张图片");
        }
    }

    private String normalizeTaskTypeForSave(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toUpperCase();
        if (normalized.isBlank()) {
            return DEFAULT_TASK_TYPE;
        }
        if (!SUPPORTED_TASK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的任务分类");
        }
        return normalized;
    }

    private String normalizeTaskTypeForFilter(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toUpperCase();
        if (normalized.isBlank()) {
            return "";
        }
        if (!SUPPORTED_TASK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的任务分类");
        }
        return normalized;
    }

    private String toJsonImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = urls.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .limit(6)
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("图片列表格式错误");
        }
    }

    private void markOverdueTasks() {
        List<DeliveryTask> unclaimedExpiredTasks = deliveryTaskRepository
                .findByStatusAndLatestDeliveryAtLessThanEqual(TaskStatus.OPEN, LocalDateTime.now());
        for (DeliveryTask task : unclaimedExpiredTasks) {
            try {
                self.getObject().settleUnclaimedExpiredTaskInTx(task);
            } catch (Exception ex) {
                // 单条超时结算失败不阻塞发布/查询
            }
        }

        List<DeliveryTask> overdueTasks = deliveryTaskRepository
                .findByStatusAndLatestDeliveryAtBefore(TaskStatus.CLAIMED, LocalDateTime.now());
        for (DeliveryTask task : overdueTasks) {
            try {
                self.getObject().settleTimeoutTaskInTx(task);
            } catch (Exception ex) {
                // 单条超时结算失败不阻塞发布/查询
            }
        }
    }

    /** 过期无人接单：本地取消 + Feign 退款，不走 Seata 全局事务，避免拖垮发布接口 */
    @Transactional
    public DeliveryTask settleUnclaimedExpiredTaskInTx(DeliveryTask task) {
        return settleUnclaimedExpiredTask(task);
    }

    /** 接单超时：本地更新 + Feign 结算，不走 Seata 全局事务 */
    @Transactional
    public DeliveryTask settleTimeoutTaskInTx(DeliveryTask task) {
        return settleTimeoutTask(task);
    }

    @Transactional
    public DeliveryTask settlePendingConfirmationTaskInGlobalTx(DeliveryTask task, String eventType) {
        return settlePendingConfirmationTask(task, eventType);
    }

    private DeliveryTask refreshTaskOverdue(DeliveryTask task) {
        if (task.getStatus() == TaskStatus.OPEN
                && task.getLatestDeliveryAt() != null
                && !task.getLatestDeliveryAt().isAfter(LocalDateTime.now())) {
            return self.getObject().settleUnclaimedExpiredTaskInTx(task);
        }
        if (task.getStatus() == TaskStatus.CLAIMED
                && task.getLatestDeliveryAt() != null
                && task.getLatestDeliveryAt().isBefore(LocalDateTime.now())) {
            return self.getObject().settleTimeoutTaskInTx(task);
        }
        return task;
    }

    @Scheduled(fixedDelay = 60000)
    public void autoSettleOverdueTasks() {
        markOverdueTasks();
    }

    @Scheduled(fixedDelayString = "${app.task.auto-approve-check-ms:60000}")
    @Transactional
    public void autoApprovePrepaidPendingReviewTasks() {
        if (!autoApproveAfterBounty) {
            return;
        }
        List<DeliveryTask> tasks = deliveryTaskRepository
                .findTop50ByStatusAndBountyPrepaidOrderByCreatedAtAsc(TaskStatus.PENDING_REVIEW, true);
        for (DeliveryTask task : tasks) {
            try {
                autoApproveAfterBountyIfEligible(task, "system");
            } catch (Exception ignore) {
                // 单条失败不影响后续
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.task.abandoned-pending-review-check-ms:300000}")
    @Transactional
    public void cancelAbandonedPendingReviewTasks() {
        if (abandonedPendingReviewHours <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusHours(abandonedPendingReviewHours);
        List<DeliveryTask> tasks = deliveryTaskRepository
                .findByStatusAndBountyPrepaidAndCreatedAtBefore(
                        TaskStatus.PENDING_REVIEW, false, deadline);
        for (DeliveryTask task : tasks) {
            try {
                task.setStatus(TaskStatus.CANCELLED);
                DeliveryTask saved = saveTask(task);
                pushTaskStatusChanged(saved, "TASK_CANCELLED");
            } catch (Exception ignore) {
                // 单条失败不影响后续
            }
        }
    }

    private DeliveryTask autoApproveAfterBountyIfEligible(DeliveryTask task, String operator) {
        if (!autoApproveAfterBounty || task == null) {
            return task;
        }
        if (task.getStatus() != TaskStatus.PENDING_REVIEW || !Boolean.TRUE.equals(task.getBountyPrepaid())) {
            return task;
        }
        task.setStatus(TaskStatus.OPEN);
        task.setAdminRejectRemark(null);
        DeliveryTask saved = saveTask(task);
        String auditOperator = operator == null || operator.isBlank() ? "system" : operator.trim();
        remoteAuditLog.log(auditOperator, "TASK_AUTO_APPROVED", saved.getId(), "赏金已支付，自动审核通过：" + saved.getTitle());
        pushTaskStatusChanged(saved, "TASK_APPROVED");
        return saved;
    }

    @Scheduled(fixedDelayString = "${app.task.pending-confirm-check-ms:60000}")
    public void autoConfirmPendingConfirmationTasks() {
        if (pendingConfirmAutoMinutes <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(pendingConfirmAutoMinutes);
        List<DeliveryTask> tasks = deliveryTaskRepository.findByStatusAndUpdatedAtBefore(TaskStatus.PENDING_CONFIRMATION, deadline);
        for (DeliveryTask task : tasks) {
            try {
                DeliveryTask saved = self.getObject().settlePendingConfirmationTaskInGlobalTx(task, "TASK_AUTO_CONFIRMED");
                String operator = task.getPublisherName() == null || task.getPublisherName().isBlank()
                        ? "system"
                        : task.getPublisherName().trim();
                remoteAuditLog.log(operator, "TASK_AUTO_CONFIRMED", saved.getId(), "超时自动确认任务完成：" + saved.getTitle());
            } catch (Exception ignore) {
                // 单条失败不影响后续自动确认
            }
        }
    }

    private DeliveryTask settlePendingConfirmationTask(DeliveryTask task, String eventType) {
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return task;
        }
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("任务不处于待确认状态");
        }

        task.setStatus(TaskStatus.COMPLETED);
        DeliveryTask saved = saveTask(task);

        if (saved.getClaimerName() != null && saved.getReward() != null) {
            walletSupport.incomeFromTask(saved.getClaimerName(), resolveClaimerIncome(saved), saved.getId(), saved.getTitle());
            recordCouponSubsidyIfNeeded(saved);
        }
        if (Boolean.TRUE.equals(saved.getBountyPrepaid())) {
            saved.setBountyPrepaid(false);
            saved = saveTask(saved);
        }

        pushTaskStatusChanged(saved, eventType);
        return saved;
    }

    private DeliveryTask settleTimeoutTask(DeliveryTask task) {
        if (task.getStatus() != TaskStatus.CLAIMED) {
            return task;
        }
        if (task.getClaimerName() == null || task.getClaimerName().isBlank()) {
            task.setStatus(TaskStatus.REJECTED);
            task.setOverdueRejected(true);
            return saveTask(task);
        }

        task.setStatus(TaskStatus.REJECTED);
        task.setOverdueRejected(true);
        DeliveryTask saved = saveTask(task);

        String riderName = saved.getClaimerName().trim();
        UserAccount rider = userAccountRepository.findByUsernameIgnoreCase(riderName)
                .orElseThrow(() -> new IllegalStateException("接单员不存在，无法完成超时结算"));
        int oldCredit = CreditRules.effectiveScore(rider.getCreditScore());
        int newCredit = Math.max(0, oldCredit - CreditRules.TIMEOUT_PENALTY);
        rider.setCreditScore(newCredit);
        if (newCredit < CreditRules.MIN_TO_CLAIM) {
            rider.setBlockedUntil(LocalDateTime.now().plusHours(CreditRules.TIMEOUT_BLOCK_HOURS));
        }
        userAccountRepository.save(rider);

        if (Boolean.TRUE.equals(saved.getBountyPrepaid()) || shouldRefundPrepaidBounty(saved)) {
            walletSupport.refundTaskBounty(saved.getPublisherName(), saved.getId(), resolvePayable(saved), "任务超时");
            saved.setBountyPrepaid(false);
            restoreTaskCoupon(saved);
            saved = saveTask(saved);
        }

        if (saved.getReward() != null && saved.getReward().signum() > 0 && saved.getPublisherName() != null) {
            BigDecimal compensation = saved.getReward()
                    .multiply(CreditRules.TIMEOUT_COMPENSATION_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            walletSupport.compensatePublisherForTimeout(riderName, saved.getPublisherName(), compensation, saved.getId(), saved.getTitle());
        }
        return saved;
    }

    private DeliveryTask settleUnclaimedExpiredTask(DeliveryTask task) {
        if (task.getStatus() != TaskStatus.OPEN) {
            return task;
        }
        task.setStatus(TaskStatus.CANCELLED);
        if (Boolean.TRUE.equals(task.getBountyPrepaid()) || shouldRefundPrepaidBounty(task)) {
            walletSupport.refundTaskBounty(task.getPublisherName(), task.getId(), resolvePayable(task), "任务已过最晚送达时间且无人接单");
            task.setBountyPrepaid(false);
            restoreTaskCoupon(task);
        }
        return saveTask(task);
    }

    private boolean shouldRefundPrepaidBounty(DeliveryTask task) {
        if (task == null || task.getId() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(task.getBountyPrepaid())) {
            return true;
        }
        return alipayBountyOrderSupport.hasUnsettledAlipayBountyOrder(task.getPublisherName(), task.getId());
    }

    private DeliveryTask saveTask(DeliveryTask task) {
        DeliveryTask saved = deliveryTaskRepository.save(task);
        taskElasticsearchService.ifPresent(es -> es.sync(saved));
        return saved;
    }

    private void deleteTaskRecord(DeliveryTask task) {
        Long taskId = task.getId();
        deliveryTaskRepository.delete(task);
        if (taskId != null) {
            taskElasticsearchService.ifPresent(es -> es.delete(taskId));
        }
    }
}
