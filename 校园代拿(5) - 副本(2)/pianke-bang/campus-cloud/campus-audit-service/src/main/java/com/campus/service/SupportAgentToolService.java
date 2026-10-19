package com.campus.service;

import com.campus.client.TaskInternalClient;
import com.campus.client.WalletInternalClient;
import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import com.campus.rules.CreditRules;
import com.campus.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 片刻小新只读工具：查当前登录用户的钱包、任务、提现、信誉、认证、申诉、优惠券、加急、流水。
 * username / userId 一律取自 {@link AuthContext}，禁止从聊天文本解析用户身份。
 * 任务编号可从问句解析（仅 ID，不解析他人用户名）。
 */
@Service
public class SupportAgentToolService {

    private static final Logger log = LoggerFactory.getLogger(SupportAgentToolService.class);
    private static final Pattern TASK_ID_PATTERN = Pattern.compile(
            "(?:任务|订单|单号|#)\\s*(\\d{1,12})",
            Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WalletInternalClient walletInternalClient;
    private final TaskInternalClient taskInternalClient;
    private final UserAccountRepository userAccountRepository;

    public SupportAgentToolService(
            WalletInternalClient walletInternalClient,
            TaskInternalClient taskInternalClient,
            UserAccountRepository userAccountRepository
    ) {
        this.walletInternalClient = walletInternalClient;
        this.taskInternalClient = taskInternalClient;
        this.userAccountRepository = userAccountRepository;
    }

    public enum ToolIntent {
        NONE,
        WALLET,
        TASKS,
        TASK_DETAIL,
        TASK_TIMELINE,
        WITHDRAWALS,
        CREDIT,
        RIDER_CERT,
        APPEALS,
        COUPONS,
        URGENT_QUOTA,
        TRANSACTIONS,
        /** LLM 显式请求查知识库（非用户数据） */
        SEARCH_KNOWLEDGE
    }

    public ToolIntent detectIntent(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return ToolIntent.NONE;
        }
        if (looksLikeTaskTimeline(normalized)) {
            return ToolIntent.TASK_TIMELINE;
        }
        if (looksLikeTaskDetail(normalized)) {
            return ToolIntent.TASK_DETAIL;
        }
        if (looksLikeAppeals(normalized)) {
            return ToolIntent.APPEALS;
        }
        if (looksLikeWithdrawals(normalized)) {
            return ToolIntent.WITHDRAWALS;
        }
        if (looksLikeCoupons(normalized)) {
            return ToolIntent.COUPONS;
        }
        if (looksLikeUrgentQuota(normalized)) {
            return ToolIntent.URGENT_QUOTA;
        }
        if (looksLikeTransactions(normalized)) {
            return ToolIntent.TRANSACTIONS;
        }
        if (looksLikeCredit(normalized)) {
            return ToolIntent.CREDIT;
        }
        if (looksLikeRiderCert(normalized)) {
            return ToolIntent.RIDER_CERT;
        }
        if (looksLikeWallet(normalized)) {
            return ToolIntent.WALLET;
        }
        if (looksLikeTasks(normalized)) {
            return ToolIntent.TASKS;
        }
        return ToolIntent.NONE;
    }

    /** 执行工具；任务查询会根据问句解析 scope / taskId。 */
    public Map<String, Object> run(ToolIntent intent, String normalized) {
        String username = AuthContext.getCurrentUser();
        if (username == null || username.isBlank()) {
            return toolReply(intent, "请先登录后再查询个人数据。", List.of());
        }
        Long userId = AuthContext.getCurrentUserId();
        return switch (intent) {
            case WALLET -> replyWallet(username.trim());
            case TASKS -> replyTasks(username.trim(), userId, resolveTaskScope(normalized));
            case TASK_DETAIL -> replyTaskDetail(username.trim(), parseTaskId(normalized));
            case TASK_TIMELINE -> replyTaskTimeline(username.trim(), parseTaskId(normalized));
            case WITHDRAWALS -> replyWithdrawals(username.trim());
            case CREDIT -> replyCredit(username.trim());
            case RIDER_CERT -> replyRiderCert(username.trim());
            case APPEALS -> replyAppeals(username.trim(), userId);
            case COUPONS -> replyCoupons(username.trim());
            case URGENT_QUOTA -> replyUrgentQuota(username.trim(), userId);
            case TRANSACTIONS -> replyTransactions(username.trim());
            case SEARCH_KNOWLEDGE -> {
                Map<String, Object> data = toolReply(ToolIntent.SEARCH_KNOWLEDGE, "", List.of());
                data.put("needRag", true);
                yield data;
            }
            default -> toolReply(ToolIntent.NONE, "暂无法处理该查询。", List.of());
        };
    }

    /**
     * DeepSeek / OpenAI 兼容的 tools 定义（只读）。
     */
    public List<Map<String, Object>> openAiToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("get_wallet", "查询当前登录用户的钱包余额、累计收入、已提现、提现中金额。用户问「我的钱包」「余额多少」时用。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("list_my_tasks", "查询当前用户的任务列表。", Map.of(
                "type", "object",
                "properties", Map.of(
                        "scope", Map.of(
                                "type", "string",
                                "description", "active=进行中, open=待接单, in_progress=配送中, history=历史, all=全部",
                                "enum", List.of("active", "open", "in_progress", "history", "all")
                        )
                )
        )));
        tools.add(fn("get_task_detail", "按任务编号查询当前用户相关任务的状态详情。", Map.of(
                "type", "object",
                "properties", Map.of("task_id", Map.of("type", "integer", "description", "任务 ID")),
                "required", List.of("task_id")
        )));
        tools.add(fn("get_task_timeline", "查询任务时间线：自动确认还剩多久、是否超时、最晚送达等。", Map.of(
                "type", "object",
                "properties", Map.of("task_id", Map.of("type", "integer", "description", "任务 ID")),
                "required", List.of("task_id")
        )));
        tools.add(fn("list_withdrawals", "查询当前用户提现申请进度（账号脱敏）。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("get_credit", "查询当前用户信誉分、是否禁接、能否接单。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("get_rider_cert", "查询当前用户接单员认证状态（已通过/审核中/未认证）。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("list_appeals", "查询当前用户已裁决的申诉记录。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("list_coupons", "查询当前用户优惠券。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("get_urgent_quota", "查询本周加急配额剩余次数。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("list_transactions", "查询钱包最近流水。", Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("search_knowledge", "查询平台使用规则知识库（如何发布/提现规则/认证流程等）。用户问「怎么做」「规则是什么」且需要政策说明时调用；可与查数据工具同时调用。", Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string", "description", "用于检索的改写查询，可与用户原话相同"))
        )));
        return tools;
    }

    /**
     * 按 LLM function name 执行；args 可含 task_id / scope / query。
     * fallbackNormalized 用于缺参时从用户问句解析。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> runNamed(String toolName, Map<String, Object> args, String fallbackNormalized) {
        String username = AuthContext.getCurrentUser();
        if (username == null || username.isBlank()) {
            return toolReply(ToolIntent.NONE, "请先登录后再查询个人数据。", List.of());
        }
        String name = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> a = args == null ? Map.of() : args;
        String normalized = fallbackNormalized == null ? "" : fallbackNormalized;
        Long userId = AuthContext.getCurrentUserId();
        return switch (name) {
            case "get_wallet" -> replyWallet(username.trim());
            case "list_my_tasks" -> {
                String scope = stringArg(a, "scope");
                if (scope == null || scope.isBlank()) {
                    scope = resolveTaskScope(normalized);
                }
                yield replyTasks(username.trim(), userId, scope);
            }
            case "get_task_detail" -> {
                Long id = longArg(a, "task_id");
                if (id == null) {
                    id = parseTaskId(normalized);
                }
                yield replyTaskDetail(username.trim(), id);
            }
            case "get_task_timeline" -> {
                Long id = longArg(a, "task_id");
                if (id == null) {
                    id = parseTaskId(normalized);
                }
                yield replyTaskTimeline(username.trim(), id);
            }
            case "list_withdrawals" -> replyWithdrawals(username.trim());
            case "get_credit" -> replyCredit(username.trim());
            case "get_rider_cert" -> replyRiderCert(username.trim());
            case "list_appeals" -> replyAppeals(username.trim(), userId);
            case "list_coupons" -> replyCoupons(username.trim());
            case "get_urgent_quota" -> replyUrgentQuota(username.trim(), userId);
            case "list_transactions" -> replyTransactions(username.trim());
            case "search_knowledge" -> {
                Map<String, Object> data = toolReply(ToolIntent.SEARCH_KNOWLEDGE, "", List.of());
                data.put("needRag", true);
                Object q = a.get("query");
                if (q != null && !String.valueOf(q).isBlank()) {
                    data.put("ragQuery", String.valueOf(q).trim());
                }
                yield data;
            }
            default -> toolReply(ToolIntent.NONE, "未知工具：" + name, List.of());
        };
    }

    private static Map<String, Object> fn(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (s.isBlank()) {
                return null;
            }
            return Long.parseLong(s.replaceAll("\\.0$", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> replyWallet(String username) {
        try {
            Map<String, Object> snap = walletInternalClient.agentSnapshot(username);
            if (snap == null) {
                return toolReply(ToolIntent.WALLET, "暂时查不到钱包信息，请稍后再试或点「转人工」。", List.of("suggest_handoff"));
            }
            if (snap.get("error") != null) {
                return toolReply(ToolIntent.WALLET, String.valueOf(snap.get("error")), List.of("suggest_handoff"));
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "wallet");
            card.put("balance", snap.get("balance"));
            card.put("totalIncome", snap.get("totalIncome"));
            card.put("totalWithdrawn", snap.get("totalWithdrawn"));
            card.put("withdrawingAmount", snap.get("withdrawingAmount"));
            Map<String, Object> data = toolReply(ToolIntent.WALLET, "这是你当前的钱包概况。", List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent wallet tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.WALLET, "钱包查询暂时失败，请稍后再试，或到「我的 → 钱包」查看。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyTasks(String username, Long userId, String scope) {
        try {
            Map<String, Object> summary = taskInternalClient.agentSummary(username, userId, scope, 5);
            if (summary == null) {
                return toolReply(ToolIntent.TASKS, "暂时查不到任务列表，请稍后再试。", List.of("suggest_handoff"));
            }
            if (summary.get("error") != null) {
                return toolReply(ToolIntent.TASKS, String.valueOf(summary.get("error")), List.of("suggest_handoff"));
            }
            int matched = toInt(summary.get("matched") != null ? summary.get("matched") : summary.get("total"));
            int shown = toInt(summary.get("shown"));
            String scopeLabel = safeText(summary.get("scopeLabel"));
            if (scopeLabel.isBlank()) {
                scopeLabel = scopeLabelOf(scope);
            }
            Object rawTasks = summary.get("tasks");
            List<Map<String, Object>> tasks = rawTasks instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list
                    : List.of();

            List<Map<String, Object>> taskCards = new ArrayList<>();
            for (Map<String, Object> t : tasks) {
                if (t == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.get("id"));
                row.put("title", safeText(t.get("title")));
                row.put("status", safeText(t.get("status")));
                row.put("statusLabel", safeText(t.get("statusLabel")));
                row.put("reward", t.get("reward"));
                row.put("role", safeText(t.get("role")));
                row.put("roleLabel", roleLabel(String.valueOf(t.getOrDefault("role", ""))));
                taskCards.add(row);
            }
            if (shown <= 0) {
                shown = taskCards.size();
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "tasks");
            card.put("scope", scope);
            card.put("scopeLabel", scopeLabel);
            card.put("total", matched);
            card.put("matched", matched);
            card.put("shown", shown);
            card.put("tasks", taskCards);

            String reply = buildTaskReply(scope, scopeLabel, matched, shown, taskCards.isEmpty());
            Map<String, Object> data = toolReply(ToolIntent.TASKS, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent task tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.TASKS, "任务查询暂时失败，请稍后再试，或到「我的任务」查看。", List.of("suggest_handoff"));
        }
    }

    private Map<String, Object> replyTaskDetail(String username, Long taskId) {
        if (taskId == null) {
            return toolReply(
                    ToolIntent.TASK_DETAIL,
                    "请告诉我任务编号，例如「任务 12 到哪了」。也可以先说「我的任务」查看列表后再点开某一单。",
                    List.of()
            );
        }
        try {
            Map<String, Object> detail = taskInternalClient.agentDetail(username, taskId);
            if (detail == null) {
                return toolReply(ToolIntent.TASK_DETAIL, "暂时查不到该任务，请稍后再试。", List.of("suggest_handoff"));
            }
            if (detail.get("error") != null) {
                String code = safeText(detail.get("code"));
                List<String> actions = "FORBIDDEN".equals(code) || "NOT_FOUND".equals(code)
                        ? List.of()
                        : List.of("suggest_handoff");
                return toolReply(ToolIntent.TASK_DETAIL, String.valueOf(detail.get("error")), actions);
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "task_detail");
            card.put("id", detail.get("id"));
            card.put("title", safeText(detail.get("title")));
            card.put("status", safeText(detail.get("status")));
            card.put("statusLabel", safeText(detail.get("statusLabel")));
            card.put("reward", detail.get("reward"));
            card.put("role", safeText(detail.get("role")));
            card.put("roleLabel", safeText(detail.get("roleLabel")));
            card.put("pickupLocation", safeText(detail.get("pickupLocation")));
            card.put("dropoffLocation", safeText(detail.get("dropoffLocation")));
            card.put("latestDeliveryAt", safeText(detail.get("latestDeliveryAt")));
            card.put("updatedAt", safeText(detail.get("updatedAt")));
            card.put("bountyPrepaid", detail.get("bountyPrepaid"));

            String statusLabel = safeText(detail.get("statusLabel"));
            String roleLabel = safeText(detail.get("roleLabel"));
            String reply = "任务 #" + taskId + "（" + roleLabel + "）当前状态：" + statusLabel + "。";
            if ("待发布方确认".equals(statusLabel)) {
                reply += " 发布者确认后酬劳才会到账；约 30 分钟未确认会自动确认。";
            } else if ("待支付赏金".equals(statusLabel)) {
                reply += " 需先支付预付赏金，否则约 1 小时后可能自动取消。";
            } else if ("接单员超时".equals(statusLabel)) {
                reply += " 超时单会按规则退还/补偿，可到任务详情或申诉入口查看。";
            }

            Map<String, Object> data = toolReply(ToolIntent.TASK_DETAIL, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent task-detail tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.TASK_DETAIL, "任务详情查询暂时失败，请稍后再试。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyTaskTimeline(String username, Long taskId) {
        try {
            if (taskId == null || taskId <= 0) {
                return toolReply(
                        ToolIntent.TASK_TIMELINE,
                        "请告诉我任务编号，例如「任务 12 还要多久自动确认」。",
                        List.of()
                );
            }
            Map<String, Object> detail = taskInternalClient.agentTimeline(username, taskId);
            if (detail == null) {
                return toolReply(ToolIntent.TASK_TIMELINE, "暂时查不到该任务时间线，请稍后再试。", List.of("suggest_handoff"));
            }
            if (detail.get("error") != null) {
                String code = safeText(detail.get("code"));
                List<String> actions = "FORBIDDEN".equals(code) || "NOT_FOUND".equals(code)
                        ? List.of()
                        : List.of("suggest_handoff");
                return toolReply(ToolIntent.TASK_TIMELINE, String.valueOf(detail.get("error")), actions);
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "task_detail");
            card.put("id", detail.get("id"));
            card.put("title", safeText(detail.get("title")));
            card.put("status", safeText(detail.get("status")));
            card.put("statusLabel", safeText(detail.get("statusLabel")));
            card.put("reward", detail.get("reward"));
            card.put("role", safeText(detail.get("role")));
            card.put("roleLabel", safeText(detail.get("roleLabel")));
            card.put("pickupLocation", safeText(detail.get("pickupLocation")));
            card.put("dropoffLocation", safeText(detail.get("dropoffLocation")));
            card.put("latestDeliveryAt", safeText(detail.get("latestDeliveryAt")));
            card.put("updatedAt", safeText(detail.get("updatedAt")));
            card.put("bountyPrepaid", detail.get("bountyPrepaid"));
            card.put("autoConfirmAt", safeText(detail.get("autoConfirmAt")));
            card.put("autoConfirmRemainingMinutes", detail.get("autoConfirmRemainingMinutes"));
            card.put("timelineHint", safeText(detail.get("timelineHint")));

            String statusLabel = safeText(detail.get("statusLabel"));
            String hint = safeText(detail.get("timelineHint"));
            String reply = "任务 #" + taskId + " 当前：" + statusLabel + "。";
            if (!hint.isBlank()) {
                reply += " " + hint + "。";
            }
            Object rem = detail.get("autoConfirmRemainingMinutes");
            if (rem != null && "待发布方确认".equals(statusLabel)) {
                reply += " 自动确认窗口约 " + detail.getOrDefault("autoConfirmMinutes", 30) + " 分钟。";
            }

            Map<String, Object> data = toolReply(ToolIntent.TASK_TIMELINE, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent task-timeline tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.TASK_TIMELINE, "任务时间线查询暂时失败，请稍后再试。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyWithdrawals(String username) {
        try {
            Map<String, Object> raw = walletInternalClient.agentWithdrawals(username, 5);
            if (raw == null) {
                return toolReply(ToolIntent.WITHDRAWALS, "暂时查不到提现记录，请稍后再试。", List.of("suggest_handoff"));
            }
            if (raw.get("error") != null) {
                return toolReply(ToolIntent.WITHDRAWALS, String.valueOf(raw.get("error")), List.of("suggest_handoff"));
            }
            int matched = toInt(raw.get("matched"));
            Object itemsRaw = raw.get("items");
            List<Map<String, Object>> items = itemsRaw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list
                    : List.of();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> it : items) {
                if (it == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", it.get("id"));
                row.put("amount", it.get("amount"));
                row.put("fee", it.get("fee"));
                row.put("payout", it.get("payout"));
                row.put("status", safeText(it.get("status")));
                row.put("statusLabel", safeText(it.get("statusLabel")));
                row.put("accountMasked", safeText(it.get("accountMasked")));
                row.put("auditRemark", safeText(it.get("auditRemark")));
                row.put("createdAt", safeText(it.get("createdAt")));
                rows.add(row);
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "withdrawals");
            card.put("matched", matched);
            card.put("shown", rows.size());
            card.put("items", rows);

            String reply;
            if (rows.isEmpty()) {
                reply = "你目前没有提现申请记录。仅认证接单员可提现；规则可问「怎么提现」。";
            } else {
                Map<String, Object> latest = rows.get(0);
                reply = "最近一笔提现：" + safeText(latest.get("statusLabel"))
                        + "，金额 ¥" + money(latest.get("amount"))
                        + "（预计到账 ¥" + money(latest.get("payout")) + "）。"
                        + "审核通过后一般 1～3 个工作日到账。";
            }
            Map<String, Object> data = toolReply(ToolIntent.WITHDRAWALS, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent withdrawals tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.WITHDRAWALS, "提现记录查询暂时失败，请到「我的钱包」查看。", List.of("suggest_handoff"));
        }
    }

    private Map<String, Object> replyCredit(String username) {
        try {
            UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                    .orElse(null);
            if (user == null) {
                return toolReply(ToolIntent.CREDIT, "暂时查不到账号信誉信息。", List.of("suggest_handoff"));
            }
            int score = CreditRules.effectiveScore(user.getCreditScore());
            LocalDateTime blockedUntil = user.getBlockedUntil();
            boolean blocked = CreditRules.isBlocked(blockedUntil, LocalDateTime.now());
            boolean rider = user.isRiderApproved();
            boolean canClaim = rider && CreditRules.canClaimByCredit(user.getCreditScore(), blockedUntil, LocalDateTime.now());

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "credit");
            card.put("creditScore", score);
            card.put("minToClaim", CreditRules.MIN_TO_CLAIM);
            card.put("riderApproved", rider);
            card.put("blocked", blocked);
            card.put("blockedUntil", blocked ? blockedUntil.format(TIME_FMT) : "");
            card.put("canClaim", canClaim);

            String reply = "你当前信誉分为 " + score + " 分（接单需 ≥ " + CreditRules.MIN_TO_CLAIM + "）。";
            if (!rider) {
                reply += " 你还不是认证接单员，需先完成接单员申请。";
            } else if (blocked) {
                reply += " 当前禁止接单至 " + blockedUntil.format(TIME_FMT) + "。";
            } else if (score < CreditRules.MIN_TO_CLAIM) {
                reply += " 分数偏低暂时不能接单；系统会按日自动恢复（每天最多 +" + CreditRules.RECOVERY_DAILY_POINTS + "）。";
            } else {
                reply += " 当前可以接单。";
            }

            Map<String, Object> data = toolReply(ToolIntent.CREDIT, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent credit tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.CREDIT, "信誉分查询暂时失败，请稍后再试。", List.of("suggest_handoff"));
        }
    }

    private Map<String, Object> replyRiderCert(String username) {
        try {
            UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                    .orElse(null);
            if (user == null) {
                return toolReply(ToolIntent.RIDER_CERT, "暂时查不到接单员认证状态。", List.of("suggest_handoff"));
            }
            boolean approved = user.isRiderApproved();
            boolean pending = user.isRiderApplicationPending();
            String statusLabel;
            String reply;
            if (approved) {
                statusLabel = "已通过";
                reply = "你的接单员认证已通过，可以在大厅接单，并在钱包申请提现。";
            } else if (pending) {
                statusLabel = "审核中";
                reply = "你的接单员申请正在审核中（一般 1～3 个工作日）。审核期间仍可发布任务，但暂不能接单/提现。";
            } else {
                statusLabel = "未认证";
                reply = "你当前还不是接单员。可到「接单员申请」提交资料；通过后才能接单与提现。";
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "rider_cert");
            card.put("riderApproved", approved);
            card.put("riderApplicationPending", pending);
            card.put("statusLabel", statusLabel);
            card.put("appliedAt", user.getRiderAppliedAt() == null ? "" : user.getRiderAppliedAt().format(TIME_FMT));

            Map<String, Object> data = toolReply(ToolIntent.RIDER_CERT, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent rider-cert tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.RIDER_CERT, "认证状态查询暂时失败，请稍后再试。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyAppeals(String username, Long userId) {
        try {
            Map<String, Object> raw = taskInternalClient.agentAppeals(username, userId, 5);
            if (raw == null) {
                return toolReply(ToolIntent.APPEALS, "暂时查不到申诉记录，请稍后再试。", List.of("suggest_handoff"));
            }
            if (raw.get("error") != null) {
                return toolReply(ToolIntent.APPEALS, String.valueOf(raw.get("error")), List.of("suggest_handoff"));
            }
            int matched = toInt(raw.get("matched"));
            Object itemsRaw = raw.get("items");
            List<Map<String, Object>> items = itemsRaw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list
                    : List.of();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> it : items) {
                if (it == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskId", it.get("taskId"));
                row.put("title", safeText(it.get("title")));
                row.put("taskStatusLabel", safeText(it.get("taskStatusLabel")));
                row.put("appealApproved", it.get("appealApproved"));
                row.put("appealStatusLabel", safeText(it.get("appealStatusLabel")));
                row.put("resultHint", safeText(it.get("resultHint")));
                row.put("appealRemark", safeText(it.get("appealRemark")));
                row.put("auditedAt", safeText(it.get("auditedAt")));
                rows.add(row);
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "appeals");
            card.put("matched", matched);
            card.put("shown", rows.size());
            card.put("items", rows);

            String reply;
            if (rows.isEmpty()) {
                reply = "暂无已裁决的申诉记录。如有争议，可在任务详情提交申诉（最多 800 字），或转人工说明。";
            } else {
                Map<String, Object> latest = rows.get(0);
                reply = "最近一条申诉：任务 #" + latest.get("taskId")
                        + " " + safeText(latest.get("appealStatusLabel"));
                String hint = safeText(latest.get("resultHint"));
                if (!hint.isBlank()) {
                    reply += "（" + hint + "）";
                }
                reply += "。";
            }
            Map<String, Object> data = toolReply(ToolIntent.APPEALS, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent appeals tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.APPEALS, "申诉查询暂时失败，请稍后再试或转人工。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyCoupons(String username) {
        try {
            Map<String, Object> raw = walletInternalClient.agentCoupons(username, 8);
            if (raw == null) {
                return toolReply(ToolIntent.COUPONS, "暂时查不到优惠券，请稍后再试。", List.of("suggest_handoff"));
            }
            if (raw.get("error") != null) {
                return toolReply(ToolIntent.COUPONS, String.valueOf(raw.get("error")), List.of("suggest_handoff"));
            }
            int unused = toInt(raw.get("unusedCount"));
            int matched = toInt(raw.get("matched"));
            Object itemsRaw = raw.get("items");
            List<Map<String, Object>> items = itemsRaw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list
                    : List.of();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> it : items) {
                if (it == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", it.get("id"));
                row.put("name", safeText(it.get("name")));
                row.put("status", safeText(it.get("status")));
                row.put("statusLabel", safeText(it.get("statusLabel")));
                row.put("discountAmount", it.get("discountAmount"));
                row.put("discountPercent", it.get("discountPercent"));
                row.put("expireAt", safeText(it.get("expireAt")));
                String banner = safeText(it.get("bannerImageUrl"));
                if (!banner.isBlank()) {
                    row.put("bannerImageUrl", banner);
                }
                rows.add(row);
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "coupons");
            card.put("matched", matched);
            card.put("unusedCount", unused);
            card.put("usedCount", toInt(raw.get("usedCount")));
            card.put("shown", rows.size());
            card.put("items", rows);

            String reply = unused > 0
                    ? "你有 " + unused + " 张可用优惠券，发布支付赏金时可抵扣。"
                    : (matched > 0
                    ? "暂无可用券（共 " + matched + " 张历史券）。可到领券中心看看活动。"
                    : "你还没有优惠券。可到领券中心领取新人券或用兑换码。");
            Map<String, Object> data = toolReply(ToolIntent.COUPONS, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent coupons tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.COUPONS, "优惠券查询暂时失败，请到「优惠券」页查看。", List.of("suggest_handoff"));
        }
    }

    private Map<String, Object> replyUrgentQuota(String username, Long userId) {
        try {
            Map<String, Object> raw = taskInternalClient.agentUrgentQuota(username, userId);
            if (raw == null) {
                return toolReply(ToolIntent.URGENT_QUOTA, "暂时查不到加急配额，请稍后再试。", List.of("suggest_handoff"));
            }
            if (raw.get("error") != null) {
                return toolReply(ToolIntent.URGENT_QUOTA, String.valueOf(raw.get("error")), List.of("suggest_handoff"));
            }
            int weeklyLimit = toInt(raw.get("weeklyLimit"));
            int used = toInt(raw.get("used"));
            int remaining = toInt(raw.get("remaining"));
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "urgent_quota");
            card.put("weeklyLimit", weeklyLimit);
            card.put("used", used);
            card.put("remaining", remaining);
            card.put("feeMin", raw.get("feeMin"));
            card.put("feeMax", raw.get("feeMax"));
            card.put("boostMinutes", raw.get("boostMinutes"));

            String reply = "本周加急已用 " + used + "/" + weeklyLimit + " 次，还可加急 "
                    + remaining + " 次。加急费约 ¥"
                    + money(raw.get("feeMin")) + "～¥" + money(raw.get("feeMax"))
                    + "，可在发布页开启。";
            Map<String, Object> data = toolReply(ToolIntent.URGENT_QUOTA, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent urgent-quota tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.URGENT_QUOTA, "加急配额查询暂时失败，请稍后再试。", List.of("suggest_handoff"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replyTransactions(String username) {
        try {
            Map<String, Object> raw = walletInternalClient.agentTransactions(username, 8);
            if (raw == null) {
                return toolReply(ToolIntent.TRANSACTIONS, "暂时查不到流水，请稍后再试。", List.of("suggest_handoff"));
            }
            if (raw.get("error") != null) {
                return toolReply(ToolIntent.TRANSACTIONS, String.valueOf(raw.get("error")), List.of("suggest_handoff"));
            }
            int matched = toInt(raw.get("matched"));
            Object itemsRaw = raw.get("items");
            List<Map<String, Object>> items = itemsRaw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list
                    : List.of();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> it : items) {
                if (it == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", it.get("id"));
                row.put("type", safeText(it.get("type")));
                row.put("typeLabel", safeText(it.get("typeLabel")));
                row.put("amount", it.get("amount"));
                row.put("balanceAfter", it.get("balanceAfter"));
                row.put("taskId", it.get("taskId"));
                row.put("remark", safeText(it.get("remark")));
                row.put("createdAt", safeText(it.get("createdAt")));
                rows.add(row);
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kind", "transactions");
            card.put("matched", matched);
            card.put("shown", rows.size());
            card.put("items", rows);

            String reply = rows.isEmpty()
                    ? "暂无钱包流水记录。"
                    : "最近流水共展示 " + rows.size() + " 条（全量约 " + matched + " 条可在钱包页查看）。";
            Map<String, Object> data = toolReply(ToolIntent.TRANSACTIONS, reply, List.of());
            data.put("toolPayload", card);
            return data;
        } catch (Exception ex) {
            log.warn("agent transactions tool failed: {}", ex.getMessage());
            return toolReply(ToolIntent.TRANSACTIONS, "流水查询暂时失败，请到「我的钱包」查看。", List.of("suggest_handoff"));
        }
    }

    private static String buildTaskReply(
            String scope,
            String scopeLabel,
            int matched,
            int shown,
            boolean empty
    ) {
        if (empty) {
            return switch (scope) {
                case "open" -> "你现在没有「待接单」的任务。发布后审核通过会出现在这里；也可说「我的历史任务」查看已取消/已完成。";
                case "in_progress" -> "你现在没有配送中的任务。可以说「待接单的任务」或「我的历史任务」。";
                case "history" -> "暂时没有历史任务记录。";
                case "all" -> "目前还没有与你相关的任务。可以去大厅发布，或接一单试试～";
                default -> "你目前没有进行中的任务。可以说「待接单的任务」「我的历史任务」再查，或点下方查看全部。";
            };
        }
        String base = "「" + scopeLabel + "」任务共 " + matched + " 条";
        if (matched > shown) {
            base += "，下面先显示 " + shown + " 条";
        }
        return base + "。";
    }

    /** 从问句解析任务范围；默认进行中，避免把历史已取消刷满屏。 */
    static String resolveTaskScope(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return "active";
        }
        String n = normalized;
        if (n.contains("历史") || n.contains("已完成") || n.contains("已取消") || n.contains("过去的")) {
            return "history";
        }
        if (n.contains("全部任务") || n.contains("所有任务") || n.contains("全部订单")) {
            return "all";
        }
        if (n.contains("待接单") || n.contains("等接单") || n.contains("还没人接") || n.contains("没人接")) {
            return "open";
        }
        if (n.contains("配送中") || n.contains("进行中") || n.contains("送着呢") || n.contains("在送")) {
            return "in_progress";
        }
        return "active";
    }

    /** 从问句解析任务编号；只解析 ID，不解析用户名。 */
    static Long parseTaskId(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        Matcher m = TASK_ID_PATTERN.matcher(normalized);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String scopeLabelOf(String scope) {
        return switch (scope == null ? "" : scope) {
            case "open" -> "待接单";
            case "in_progress" -> "进行中";
            case "history" -> "历史";
            case "all" -> "全部";
            default -> "进行中";
        };
    }

    private static Map<String, Object> toolReply(ToolIntent intent, String reply, List<String> actions) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", "agent-tool");
        data.put("source", "tool:" + (intent == ToolIntent.NONE ? "unknown" : intent.name().toLowerCase(Locale.ROOT)));
        data.put("reply", reply);
        data.put("actions", actions == null ? List.of() : actions);
        data.put("citations", List.of());
        return data;
    }

    private static boolean looksLikeTaskTimeline(String n) {
        if (parseTaskId(n) == null && !(n.contains("这一单") || n.contains("这个任务") || n.contains("该任务"))) {
            return false;
        }
        return n.contains("自动确认")
                || n.contains("还要多久")
                || n.contains("还剩多久")
                || n.contains("倒计时")
                || n.contains("什么时候确认")
                || n.contains("何时确认")
                || n.contains("超时了吗")
                || n.contains("会不会自动确认")
                || (n.contains("时间线") && (n.contains("任务") || n.contains("订单")));
    }

    private static boolean looksLikeTaskDetail(String n) {
        if (n.contains("怎么发布") || n.contains("如何发布") || n.contains("如何成为接单员")) {
            return false;
        }
        if (parseTaskId(n) != null) {
            return true;
        }
        boolean aboutOne = n.contains("这一单") || n.contains("这个任务") || n.contains("该任务") || n.contains("这笔订单");
        boolean askStatus = n.contains("到哪了") || n.contains("什么状态") || n.contains("进度")
                || n.contains("待确认") || n.contains("为什么取消") || n.contains("超时了吗");
        return aboutOne && askStatus;
    }

    private static boolean looksLikeWithdrawals(String n) {
        if (n.contains("怎么提现") || n.contains("如何提现") || n.contains("提现规则")
                || n.contains("提现手续费") || n.contains("最低提现") || n.contains("谁可以提现")
                || n.contains("提现多久")) {
            return false;
        }
        return n.contains("我的提现")
                || n.contains("提现进度")
                || n.contains("提现记录")
                || n.contains("提现申请")
                || n.contains("提现审核")
                || n.contains("提现审到")
                || n.contains("提现中")
                || n.contains("提现到账了吗")
                || (n.contains("提现") && (n.contains("审") || n.contains("进度") || n.contains("记录") || n.contains("申请")));
    }

    private static boolean looksLikeCredit(String n) {
        if (n.contains("信誉分规则") || n.contains("超时扣多少")
                || n.contains("怎么恢复信誉") || n.contains("如何恢复信誉")
                || n.contains("怎么扣信誉") || n.contains("如何扣分")) {
            return false;
        }
        // 短问 / 口语别名：信誉分、信用积分（无需再带「多少」「我的」）
        if (n.equals("信誉") || n.equals("信誉分") || n.equals("信用分") || n.equals("信用积分")
                || n.equals("我的信誉") || n.equals("我的信誉分") || n.equals("我的信用分")
                || n.equals("我的信用积分")) {
            return true;
        }
        if (n.contains("信誉分") || n.contains("信用积分") || n.contains("信用分")) {
            return true;
        }
        return n.contains("我的信誉")
                || n.contains("信誉多少")
                || n.contains("信誉分多少")
                || n.contains("信誉分低")
                || n.contains("接不了单")
                || n.contains("不能接单")
                || n.contains("禁止接单")
                || n.contains("被禁接")
                || n.contains("为什么接不了")
                || (n.contains("信誉") && (n.contains("多少") || n.contains("我的") || n.contains("查询")
                || n.contains("查一下") || n.contains("几分") || n.contains("看看") || n.contains("查下")))
                || (n.contains("信用") && (n.contains("积分") || n.contains("多少") || n.contains("我的") || n.contains("分数")));
    }

    private static boolean looksLikeRiderCert(String n) {
        if (n.contains("如何成为接单员") || n.contains("怎么申请接单员") || n.contains("怎样成为接单员")
                || n.contains("如何申请接单员")) {
            return false;
        }
        return n.contains("认证过了吗")
                || n.contains("认证状态")
                || n.contains("接单员审核")
                || n.contains("申请审了")
                || n.contains("我是接单员吗")
                || n.contains("是不是接单员")
                || n.contains("接单员通过了吗")
                || (n.contains("接单员申请") && (n.contains("我的") || n.contains("审") || n.contains("状态") || n.contains("过了")))
                || (n.contains("接单员") && (n.contains("认证") || n.contains("审核状态")));
    }

    private static boolean looksLikeAppeals(String n) {
        if (n.contains("怎么申诉") || n.contains("如何申诉") || n.contains("申诉流程") || n.contains("怎样申诉")) {
            return false;
        }
        return n.contains("我的申诉")
                || n.contains("申诉进度")
                || n.contains("申诉结果")
                || n.contains("申诉处理了吗")
                || n.contains("申诉审了")
                || n.contains("申诉到哪了")
                || (n.contains("申诉") && (n.contains("我的") || n.contains("进度") || n.contains("结果") || n.contains("处理") || n.contains("审")));
    }

    private static boolean looksLikeCoupons(String n) {
        if (n.contains("怎么用优惠券") || n.contains("如何用券") || n.contains("优惠券规则") || n.contains("什么是优惠券")) {
            return false;
        }
        return n.contains("我的优惠券")
                || n.contains("我的券")
                || n.contains("有哪些券")
                || n.contains("还有券吗")
                || n.contains("可用券")
                || n.contains("优惠券还有")
                || (n.contains("优惠券") && (n.contains("我的") || n.contains("查") || n.contains("多少") || n.contains("有没有")))
                || (n.contains("券") && (n.contains("我的") || n.contains("还有") || n.contains("可用")));
    }

    private static boolean looksLikeUrgentQuota(String n) {
        if (n.contains("怎么加急") || n.contains("如何加急") || n.contains("加急规则") || n.contains("加急是什么")) {
            return false;
        }
        return n.contains("加急几次")
                || n.contains("还能加急")
                || n.contains("加急配额")
                || n.contains("本周加急")
                || n.contains("加急次数")
                || n.contains("加急用完")
                || (n.contains("加急") && (n.contains("几次") || n.contains("剩余") || n.contains("还有") || n.contains("配额") || n.contains("本周")));
    }

    private static boolean looksLikeTransactions(String n) {
        return n.contains("我的流水")
                || n.contains("钱包流水")
                || n.contains("最近流水")
                || n.contains("交易记录")
                || n.contains("收支明细")
                || n.contains("钱从哪来")
                || n.contains("收入明细")
                || (n.contains("流水") && (n.contains("我的") || n.contains("查") || n.contains("最近") || n.contains("钱包")));
    }

    private static boolean looksLikeWallet(String n) {
        if (n.contains("怎么提现") || n.contains("如何提现") || n.contains("提现规则")
                || n.contains("提现多久") || n.contains("提现到账")
                || looksLikeWithdrawals(n) || looksLikeTransactions(n)) {
            return false;
        }
        return n.contains("我的钱包")
                || n.contains("钱包余额")
                || n.contains("查余额")
                || n.contains("查一下余额")
                || n.contains("看看余额")
                || n.contains("我有多少钱")
                || n.contains("余额多少")
                || n.contains("还有多少钱")
                || n.equals("余额")
                || n.equals("钱包")
                || (n.contains("钱包") && (n.contains("多少") || n.contains("余额") || n.contains("查")));
    }

    private static boolean looksLikeTasks(String n) {
        // 规则/能否类问法走知识库，不要刷任务列表
        if (n.contains("怎么发布") || n.contains("如何发布") || n.contains("怎么接单") || n.contains("如何接单")
                || n.contains("怎样接单") || n.contains("接单流程") || n.contains("如何成为接单员")
                || looksLikeTaskPolicyHowTo(n)
                || looksLikeTaskDetail(n)) {
            return false;
        }
        if (n.contains("待接单") || n.contains("配送中") || n.contains("我的历史任务") || n.contains("历史任务")) {
            return true;
        }
        return n.contains("我的任务")
                || n.contains("我的订单")
                || n.contains("查任务")
                || n.contains("查订单")
                || n.contains("看看任务")
                || n.contains("我发布了")
                || n.contains("我接了")
                || n.contains("我接的单")
                || n.contains("我发布的")
                || n.contains("最近任务")
                || n.contains("任务列表")
                || n.contains("订单列表")
                || (n.contains("任务") && (n.contains("我的") || n.contains("我现在") || n.contains("查一下") || n.contains("有哪些")));
    }

    /** 「可以取消已接的任务吗」等规则问，不应命中 list_my_tasks。 */
    static boolean looksLikeTaskPolicyHowTo(String n) {
        if (n == null || n.isBlank()) {
            return false;
        }
        if (n.contains("可以取消") || n.contains("能不能取消") || n.contains("能否取消")
                || n.contains("怎么取消") || n.contains("如何取消") || n.contains("怎样取消")
                || n.contains("取消已接") || n.contains("已接的任务吗") || n.contains("取消规则")) {
            return true;
        }
        return n.contains("取消") && (n.contains("可以") || n.contains("能不能") || n.contains("能否")
                || n.contains("怎么") || n.contains("如何") || n.contains("吗") || n.contains("规则"));
    }

    private static String roleLabel(String role) {
        return switch (role == null ? "" : role) {
            case "publisher" -> "你发布的";
            case "claimer" -> "你接的";
            default -> "相关";
        };
    }

    private static String money(Object v) {
        if (v == null) {
            return "0.00";
        }
        try {
            return String.format(Locale.ROOT, "%.2f", Double.parseDouble(String.valueOf(v)));
        } catch (Exception ignored) {
            return String.valueOf(v);
        }
    }

    private static String safeText(Object v) {
        if (v == null) {
            return "";
        }
        return String.valueOf(v).trim();
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
