package com.campus.controller.internal;

import com.campus.model.WalletTransaction;
import com.campus.model.WalletTransactionType;
import com.campus.model.WalletWithdrawRequest;
import com.campus.service.CouponService;
import com.campus.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/wallet")
public class WalletInternalController {

    private final WalletService walletService;
    private final CouponService couponService;

    public WalletInternalController(WalletService walletService, CouponService couponService) {
        this.walletService = walletService;
        this.couponService = couponService;
    }

    @PostMapping("/lock-bounty")
    public void lockTaskBounty(@RequestBody Map<String, Object> body) {
        walletService.lockTaskBounty(
                str(body, "publisherName"),
                decimal(body, "amount"),
                longVal(body, "taskId"),
                str(body, "title")
        );
    }

    @PostMapping("/refund-bounty")
    public void refundTaskBounty(@RequestBody Map<String, Object> body) {
        walletService.refundTaskBounty(
                str(body, "publisherName"),
                longVal(body, "taskId"),
                decimal(body, "amount"),
                str(body, "remark")
        );
    }

    @PostMapping("/income-from-task")
    public void incomeFromTask(@RequestBody Map<String, Object> body) {
        walletService.incomeFromTask(
                str(body, "claimerName"),
                decimal(body, "amount"),
                longVal(body, "taskId"),
                str(body, "title")
        );
    }

    @PostMapping("/coupon-subsidy")
    public void recordCouponSubsidy(@RequestBody Map<String, Object> body) {
        walletService.recordCouponSubsidy(
                str(body, "publisherName"),
                longVal(body, "taskId"),
                decimal(body, "discount"),
                str(body, "title")
        );
    }

    @PostMapping("/compensate-timeout")
    public void compensate(@RequestBody Map<String, Object> body) {
        walletService.compensatePublisherForTimeout(
                str(body, "riderName"),
                str(body, "publisherName"),
                decimal(body, "amount"),
                longVal(body, "taskId"),
                str(body, "title")
        );
    }

    @PostMapping("/credit-demo")
    public void creditDemo(@RequestBody Map<String, Object> body) {
        walletService.creditManualForDemo(str(body, "username"), decimal(body, "amount"));
    }

    /** Agent 只读：按用户名查看钱包概览（不含流水明细） */
    @GetMapping("/agent-snapshot")
    public Map<String, Object> agentSnapshot(@RequestParam("username") String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        var overview = walletService.overview(username.trim());
        return Map.of(
                "balance", overview.getBalance() == null ? 0 : overview.getBalance(),
                "totalIncome", overview.getTotalIncome() == null ? 0 : overview.getTotalIncome(),
                "totalWithdrawn", overview.getTotalWithdrawn() == null ? 0 : overview.getTotalWithdrawn(),
                "withdrawingAmount", overview.getWithdrawingAmount() == null ? 0 : overview.getWithdrawingAmount()
        );
    }

    /** Agent 只读：当前用户最近提现申请（不含完整账号） */
    @GetMapping("/agent-withdrawals")
    public Map<String, Object> agentWithdrawals(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        int take = (limit == null || limit <= 0) ? 5 : Math.min(8, limit);
        List<WalletWithdrawRequest> all = walletService.myWithdraws(username.trim());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        int end = Math.min(all.size(), take);
        for (int i = 0; i < end; i++) {
            WalletWithdrawRequest wr = all.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", wr.getId());
            row.put("amount", wr.getAmount());
            row.put("fee", WalletService.calcWithdrawFee(wr.getAmount()));
            row.put("payout", WalletService.calcWithdrawPayout(wr.getAmount()));
            row.put("status", wr.getStatus() == null ? "" : wr.getStatus().name());
            row.put("statusLabel", withdrawStatusLabel(wr.getStatus() == null ? "" : wr.getStatus().name()));
            row.put("accountMasked", maskAccount(wr.getAccountNo()));
            row.put("auditRemark", wr.getAuditRemark() == null ? "" : wr.getAuditRemark());
            row.put("createdAt", wr.getCreatedAt() == null ? "" : wr.getCreatedAt().format(fmt));
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username.trim());
        data.put("matched", all.size());
        data.put("shown", items.size());
        data.put("items", items);
        return data;
    }

    /** Agent 只读：我的优惠券摘要 */
    @GetMapping("/agent-coupons")
    public Map<String, Object> agentCoupons(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        int take = (limit == null || limit <= 0) ? 8 : Math.min(12, limit);
        List<Map<String, Object>> all = couponService.listForAgent(username.trim(), null);
        int unused = 0;
        int used = 0;
        int locked = 0;
        int expired = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> c : all) {
            String st = String.valueOf(c.getOrDefault("status", ""));
            switch (st) {
                case "UNUSED" -> unused++;
                case "USED" -> used++;
                case "LOCKED" -> locked++;
                case "EXPIRED" -> expired++;
                default -> {
                }
            }
        }
        // 优先展示可用券，再补其他
        List<Map<String, Object>> prioritized = new ArrayList<>();
        for (Map<String, Object> c : all) {
            if ("UNUSED".equals(String.valueOf(c.get("status")))) {
                prioritized.add(c);
            }
        }
        for (Map<String, Object> c : all) {
            if (!"UNUSED".equals(String.valueOf(c.get("status")))) {
                prioritized.add(c);
            }
        }
        int end = Math.min(prioritized.size(), take);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < end; i++) {
            Map<String, Object> c = prioritized.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.get("id"));
            row.put("name", c.getOrDefault("name", "优惠券"));
            row.put("status", c.get("status"));
            row.put("statusLabel", couponStatusLabel(String.valueOf(c.getOrDefault("status", ""))));
            row.put("type", c.get("type"));
            row.put("discountAmount", c.get("discountAmount"));
            row.put("discountPercent", c.get("discountPercent"));
            row.put("minSpend", c.get("minSpend"));
            Object banner = c.get("bannerImageUrl");
            if (banner != null && !String.valueOf(banner).isBlank()) {
                row.put("bannerImageUrl", String.valueOf(banner).trim());
            }
            Object expireAt = c.get("expireAt");
            if (expireAt instanceof java.time.LocalDateTime ldt) {
                row.put("expireAt", ldt.format(fmt));
            } else {
                row.put("expireAt", expireAt == null ? "" : String.valueOf(expireAt));
            }
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username.trim());
        data.put("matched", all.size());
        data.put("shown", items.size());
        data.put("unusedCount", unused);
        data.put("usedCount", used);
        data.put("lockedCount", locked);
        data.put("expiredCount", expired);
        data.put("items", items);
        return data;
    }

    /** Agent 只读：最近钱包流水 */
    @GetMapping("/agent-transactions")
    public Map<String, Object> agentTransactions(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        int take = (limit == null || limit <= 0) ? 8 : Math.min(12, limit);
        List<WalletTransaction> all = walletService.myTransactions(username.trim());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        int end = Math.min(all.size(), take);
        for (int i = 0; i < end; i++) {
            WalletTransaction t = all.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("type", t.getType() == null ? "" : t.getType().name());
            row.put("typeLabel", txTypeLabel(t.getType()));
            row.put("amount", t.getAmount());
            row.put("balanceAfter", t.getBalanceAfter());
            row.put("taskId", t.getTaskId());
            row.put("remark", t.getRemark() == null ? "" : t.getRemark());
            row.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().format(fmt));
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username.trim());
        data.put("matched", all.size());
        data.put("shown", items.size());
        data.put("items", items);
        return data;
    }

    private static String couponStatusLabel(String status) {
        return switch (status) {
            case "UNUSED" -> "可用";
            case "USED" -> "已使用";
            case "LOCKED" -> "占用中";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }

    private static String txTypeLabel(WalletTransactionType type) {
        if (type == null) {
            return "流水";
        }
        return switch (type) {
            case TASK_INCOME -> "任务收入";
            case TASK_BOUNTY_LOCK -> "预付赏金";
            case TASK_BOUNTY_REFUND -> "赏金退还";
            case TIMEOUT_COMPENSATION_DEBIT -> "超时补偿扣款";
            case TIMEOUT_COMPENSATION_CREDIT -> "超时补偿入账";
            case TIMEOUT_COMPENSATION_DEBT -> "超时补偿欠款";
            case TIMEOUT_COMPENSATION_DEBT_REPAY -> "欠款抵扣";
            case MANUAL_BONUS -> "奖励";
            case ADMIN_CREDIT -> "管理员充值";
            case ALIPAY_RECHARGE -> "支付宝充值";
            case ALIPAY_WITHDRAW -> "支付宝提现";
            case WITHDRAW_APPLY -> "提现申请";
            case WITHDRAW_APPROVE -> "提现通过";
            case WITHDRAW_REJECT -> "提现驳回";
            case COUPON_SUBSIDY -> "优惠券补贴";
        };
    }

    private static String withdrawStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "审核中";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }

    private static String maskAccount(String accountNo) {
        if (accountNo == null) {
            return "";
        }
        String raw = accountNo.trim();
        if (raw.length() <= 4) {
            return raw.isEmpty() ? "" : "****";
        }
        return "****" + raw.substring(raw.length() - 4);
    }

    private static String str(Map<String, Object> body, String key) {
        return String.valueOf(body.getOrDefault(key, "")).trim();
    }

    private static Long longVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private static BigDecimal decimal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(v));
    }
}
