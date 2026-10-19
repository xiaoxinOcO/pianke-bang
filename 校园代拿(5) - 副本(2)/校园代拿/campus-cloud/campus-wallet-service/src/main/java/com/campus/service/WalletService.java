package com.campus.service;

import com.campus.dto.AdminWalletCreditRequest;
import com.campus.dto.WalletAuditRequest;
import com.campus.dto.WalletOverviewDTO;
import com.campus.dto.WalletWithdrawApplyRequest;
import com.campus.model.*;
import com.campus.repository.DeliveryTaskRepository;
import com.campus.repository.UserAccountRepository;
import com.campus.repository.WalletAccountRepository;
import com.campus.repository.WalletTransactionRepository;
import com.campus.repository.WalletWithdrawRequestRepository;
import com.campus.security.AdminPolicy;
import com.campus.support.RemoteAuditLogSupport;
import com.campus.support.RemoteSystemMessageSupport;
import com.campus.support.WalletAccountSupport;
import com.campus.support.WalletBountyCore;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WalletService {

    public static final String ALIPAY_SANDBOX_WITHDRAW_REMARK = "ALIPAY_SANDBOX";

    /** 单笔最低提现 */
    public static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("1.00");
    /** 提现金额步进（0.1 元） */
    public static final BigDecimal WITHDRAW_AMOUNT_STEP = new BigDecimal("0.10");
    /** 低于该金额收取小额提现手续费 */
    public static final BigDecimal WITHDRAW_FEE_THRESHOLD = new BigDecimal("100.00");
    /** 小额提现手续费（元/笔） */
    public static final BigDecimal SMALL_WITHDRAW_FEE = new BigDecimal("0.10");

    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletWithdrawRequestRepository walletWithdrawRequestRepository;
    private final DeliveryTaskRepository deliveryTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final RemoteAuditLogSupport remoteAuditLog;
    private final ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport;
    private final AdminPolicy adminPolicy;
    private final ObjectProvider<AlipayService> alipayServiceProvider;
    private final WalletAccountSupport walletAccountSupport;
    private final WalletBountyCore walletBountyCore;

    public WalletService(
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletWithdrawRequestRepository walletWithdrawRequestRepository,
            DeliveryTaskRepository deliveryTaskRepository,
            UserAccountRepository userAccountRepository,
            RemoteAuditLogSupport remoteAuditLog,
            ObjectProvider<RemoteSystemMessageSupport> systemMessageSupport,
            AdminPolicy adminPolicy,
            ObjectProvider<AlipayService> alipayServiceProvider,
            WalletAccountSupport walletAccountSupport,
            WalletBountyCore walletBountyCore
    ) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletWithdrawRequestRepository = walletWithdrawRequestRepository;
        this.deliveryTaskRepository = deliveryTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.remoteAuditLog = remoteAuditLog;
        this.systemMessageSupport = systemMessageSupport;
        this.adminPolicy = adminPolicy;
        this.alipayServiceProvider = alipayServiceProvider;
        this.walletAccountSupport = walletAccountSupport;
        this.walletBountyCore = walletBountyCore;
    }

    private String requireUser(String username) {
        return walletAccountSupport.resolveCanonicalUsername(username);
    }

    private WalletAccount getOrCreate(String username) {
        return walletAccountSupport.getOrCreateMerged(username);
    }

    @Transactional
    public WalletOverviewDTO overview(String username) {
        String user = requireUser(username);
        WalletAccount w = getOrCreate(user);
        // 以提现申请单为准重算，避免 wallet_account 计数与待审/已通过记录漂移
        BigDecimal[] withdrawStats = summarizeWithdrawAmounts(user);
        BigDecimal withdrawn = withdrawStats[0];
        BigDecimal withdrawing = withdrawStats[1];
        boolean dirty = false;
        if (w.getTotalWithdrawn() == null || w.getTotalWithdrawn().compareTo(withdrawn) != 0) {
            w.setTotalWithdrawn(withdrawn);
            dirty = true;
        }
        if (w.getWithdrawingAmount() == null || w.getWithdrawingAmount().compareTo(withdrawing) != 0) {
            w.setWithdrawingAmount(withdrawing);
            dirty = true;
        }
        if (dirty) {
            walletAccountRepository.save(w);
        }
        return new WalletOverviewDTO(w.getBalance(), w.getTotalIncome(), withdrawn, withdrawing);
    }

    /**
     * @return [totalWithdrawn(APPROVED), withdrawingAmount(PENDING)]
     */
    private BigDecimal[] summarizeWithdrawAmounts(String canonicalUsername) {
        BigDecimal withdrawn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawing = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<Long, WalletWithdrawRequest> byId = new LinkedHashMap<>();
        for (String alias : walletAccountSupport.listUsernameAliases(canonicalUsername)) {
            for (WalletWithdrawRequest wr : walletWithdrawRequestRepository.findAllByUsernameIgnoreCase(alias)) {
                if (wr != null && wr.getId() != null) {
                    byId.put(wr.getId(), wr);
                }
            }
            for (WalletWithdrawRequest wr : walletWithdrawRequestRepository.findAllByUsername(alias)) {
                if (wr != null && wr.getId() != null) {
                    byId.put(wr.getId(), wr);
                }
            }
        }
        for (WalletWithdrawRequest wr : byId.values()) {
            if (wr.getAmount() == null || wr.getStatus() == null) {
                continue;
            }
            BigDecimal amt = wr.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (wr.getStatus() == WithdrawStatus.APPROVED) {
                withdrawn = withdrawn.add(amt);
            } else if (wr.getStatus() == WithdrawStatus.PENDING) {
                withdrawing = withdrawing.add(amt);
            }
        }
        return new BigDecimal[]{withdrawn, withdrawing};
    }

    public List<WalletTransaction> myTransactions(String username) {
        String user = requireUser(username);
        return walletTransactionRepository.findTop50ByUsernameOrderByCreatedAtDesc(user);
    }

    public List<WalletWithdrawRequest> myWithdraws(String username) {
        String user = requireUser(username);
        Map<Long, WalletWithdrawRequest> byId = new LinkedHashMap<>();
        for (String alias : walletAccountSupport.listUsernameAliases(user)) {
            for (WalletWithdrawRequest wr : walletWithdrawRequestRepository.findAllByUsernameIgnoreCase(alias)) {
                if (wr != null && wr.getId() != null) {
                    byId.put(wr.getId(), wr);
                }
            }
            for (WalletWithdrawRequest wr : walletWithdrawRequestRepository.findAllByUsername(alias)) {
                if (wr != null && wr.getId() != null) {
                    byId.put(wr.getId(), wr);
                }
            }
        }
        List<WalletWithdrawRequest> rows = new ArrayList<>(byId.values());
        rows.sort((a, b) -> {
            var at = a.getCreatedAt();
            var bt = b.getCreatedAt();
            if (at == null && bt == null) return 0;
            if (at == null) return 1;
            if (bt == null) return -1;
            return bt.compareTo(at);
        });
        if (rows.size() <= 30) {
            return rows;
        }
        return rows.subList(0, 30);
    }

    /**
     * 发布任务时冻结发布者钱包赏金。
     */
    @Transactional
    public void lockTaskBounty(String publisherName, BigDecimal amount, Long taskId, String title) {
        walletBountyCore.lockTaskBounty(publisherName, amount, taskId, title);
    }

    /**
     * 任务驳回或取消时退还发布者赏金：支付宝支付的走原路退回，钱包冻结的退回钱包。
     */
    @Transactional
    public void refundTaskBounty(String publisherName, Long taskId, BigDecimal amount, String remark) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String reason = normalizeRefundReason(remark);
        AlipayService alipayService = alipayServiceProvider.getIfAvailable();
        if (alipayService != null) {
            String alipayRemark = reason + "，赏金已退回支付宝";
            if (alipayService.tryRefundTaskBountyToAlipay(publisherName, taskId, amount, alipayRemark)) {
                return;
            }
            if (alipayService.hasUnsettledTaskBountyAlipayOrder(publisherName, taskId)) {
                // 沙箱已发起支付但尚未同步/退款成功时，禁止误退到平台钱包
                return;
            }
        }
        walletBountyCore.refundTaskBountyToWallet(publisherName, taskId, amount, reason + "，赏金已退回钱包");
    }

    private static String normalizeRefundReason(String remark) {
        if (remark == null || remark.isBlank()) {
            return "任务赏金退还";
        }
        String text = remark.trim();
        // 去掉调用方误写的渠道文案，由本方法按实际退款路径补全
        text = text
                .replace("，赏金已原路退回支付宝", "")
                .replace("，赏金已退回支付宝", "")
                .replace("，赏金已退回钱包", "")
                .replace("，赏金已原路退回钱包", "")
                .replace("赏金已原路退回支付宝", "")
                .replace("赏金已退回支付宝", "")
                .replace("赏金已退回钱包", "")
                .replace("，赏金已退还", "")
                .replace("赏金已退还", "")
                .replace("原路退回支付宝", "")
                .replace("退回支付宝", "")
                .replace("退回钱包", "")
                .trim();
        if (text.endsWith("，") || text.endsWith(",")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text.isBlank() ? "任务赏金退还" : text;
    }

    /** 演示数据：为指定用户充值，便于种子任务能扣除赏金 */
    @Transactional
    public void creditManualForDemo(String username, BigDecimal amount) {
        walletBountyCore.creditManualForDemo(username, amount);
    }

    /** 支付宝沙箱充值入账 */
    @Transactional
    public void creditFromAlipayRecharge(String username, BigDecimal amount, String outTradeNo) {
        String user = requireUser(username);
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw new IllegalArgumentException("支付宝订单号不能为空");
        }

        String remark = "支付宝充值 #" + outTradeNo.trim();
        if (walletTransactionRepository.existsByUsernameAndRemark(user, remark)) {
            return;
        }

        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        WalletAccount w = getOrCreate(user);
        w.setBalance(w.getBalance().add(amt));
        w.setTotalIncome(w.getTotalIncome().add(amt));
        walletAccountRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.ALIPAY_RECHARGE);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setRemark(remark);
        walletTransactionRepository.save(t);

        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null) {
            systemMessages.notifyAlipayRecharge(user, amt, outTradeNo.trim());
        }
    }

    /**
     * 管理员为指定用户钱包充值。
     */
    @Transactional
    public Map<String, Object> adminCreditUserWallet(String admin, Long targetUserId, AdminWalletCreditRequest request) {
        if (admin == null || !adminPolicy.isAdmin(admin.trim())) {
            throw new IllegalStateException("仅管理员可执行充值");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        UserAccount target = userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("充值金额不能为空");
        }
        BigDecimal amt = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amt.signum() <= 0) {
            throw new IllegalArgumentException("充值金额必须大于0");
        }
        BigDecimal cap = new BigDecimal("100000.00");
        if (amt.compareTo(cap) > 0) {
            throw new IllegalArgumentException("单次充值金额不能超过 100000 元");
        }

        String walletUser = requireUser(target.getUsername());
        WalletAccount w = getOrCreate(walletUser);
        w.setBalance(w.getBalance().add(amt));
        w.setTotalIncome(w.getTotalIncome().add(amt));
        walletAccountRepository.save(w);

        String rem = request.getRemark() == null ? "" : request.getRemark().trim();
        if (rem.length() > 200) {
            rem = rem.substring(0, 200);
        }
        WalletTransaction t = new WalletTransaction();
        t.setUsername(walletUser);
        t.setType(WalletTransactionType.ADMIN_CREDIT);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setRemark(rem.isEmpty() ? "管理员充值" : rem);
        walletTransactionRepository.save(t);

        String detail = "向用户 " + target.getUsername() + " 充值 " + amt + (rem.isEmpty() ? "" : "，备注：" + rem);
        remoteAuditLog.log(admin.trim(), "WALLET_ADMIN_CREDIT", null, detail);

        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null) {
            systemMessages.notifyAdminRecharge(walletUser, amt, rem);
        }

        return Map.of(
                "message", "充值成功",
                "username", target.getUsername(),
                "amount", amt,
                "balanceAfter", w.getBalance()
        );
    }

    @Transactional
    public void incomeFromTask(String username, BigDecimal amount, Long taskId, String title) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String user = requireUser(username);
        boolean alreadyCredited = taskId != null && walletTransactionRepository.existsByTaskIdAndUsernameAndType(
                taskId, user, WalletTransactionType.TASK_INCOME);
        walletBountyCore.incomeFromTask(username, amount, taskId, title);
        if (alreadyCredited) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null) {
            systemMessages.notifyTaskIncome(user, amt, taskId, title);
        }
    }

    @Transactional
    public void recordCouponSubsidy(String publisherName, Long taskId, BigDecimal discount, String title) {
        walletBountyCore.recordCouponSubsidy(publisherName, taskId, discount, title);
    }

    @Transactional
    public void compensatePublisherForTimeout(String riderName, String publisherName, BigDecimal amount, Long taskId, String title) {
        walletBountyCore.compensatePublisherForTimeout(riderName, publisherName, amount, taskId, title);
    }

    @Transactional
    public WalletWithdrawRequest applyWithdraw(String username, WalletWithdrawApplyRequest request) {
        String user = requireUser(username);
        var account = userAccountRepository.findByUsernameIgnoreCase(user)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!account.isRiderApproved()) {
            throw new IllegalStateException("NEED_RIDER_FOR_WITHDRAW");
        }
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("提现金额不能为空");
        }
        BigDecimal amount = normalizeAndValidateWithdrawAmount(request.getAmount());
        BigDecimal fee = calcWithdrawFee(amount);
        BigDecimal payout = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        WalletAccount w = getOrCreate(user);
        if (w.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("钱包余额不足");
        }

        w.setBalance(w.getBalance().subtract(amount));
        w.setWithdrawingAmount(w.getWithdrawingAmount().add(amount));
        walletAccountRepository.save(w);

        String baseRemark = request.getRemark() == null ? "" : request.getRemark().trim();
        String feeRemark = fee.signum() > 0
                ? ("手续费¥" + fee.toPlainString() + "，预计到账¥" + payout.toPlainString())
                : "免手续费，预计到账¥" + payout.toPlainString();
        String remark = baseRemark.isBlank() ? feeRemark : (baseRemark + " | " + feeRemark);

        WalletWithdrawRequest r = new WalletWithdrawRequest();
        r.setUsername(user);
        r.setAmount(amount);
        r.setStatus(WithdrawStatus.PENDING);
        r.setAccountName(request.getAccountName() == null ? "" : request.getAccountName().trim());
        r.setAccountNo(request.getAccountNo() == null ? "" : request.getAccountNo().trim());
        r.setRemark(remark);
        WalletWithdrawRequest saved = walletWithdrawRequestRepository.save(r);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.WITHDRAW_APPLY);
        t.setAmount(amount.negate());
        t.setBalanceAfter(w.getBalance());
        t.setWithdrawRequestId(saved.getId());
        t.setRemark("提现申请（" + feeRemark + "）");
        walletTransactionRepository.save(t);

        RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
        if (systemMessages != null) {
            systemMessages.notifyWithdrawApply(user, amount, saved.getId());
        }

        return saved;
    }

    @Transactional
    public WalletWithdrawRequest applyAlipaySandboxWithdraw(
            String username,
            BigDecimal amount,
            String alipayAccount,
            String accountName
    ) {
        WalletWithdrawApplyRequest request = new WalletWithdrawApplyRequest();
        request.setAmount(amount);
        request.setAccountNo(alipayAccount);
        request.setAccountName(accountName);
        request.setRemark(ALIPAY_SANDBOX_WITHDRAW_REMARK);
        return applyWithdraw(username, request);
    }

    public boolean isAlipaySandboxWithdraw(WalletWithdrawRequest request) {
        if (request == null || request.getRemark() == null) {
            return false;
        }
        return request.getRemark().contains(ALIPAY_SANDBOX_WITHDRAW_REMARK);
    }

    /** 小额提现手续费：金额 &lt; 100 收 0.1，否则免费 */
    public static BigDecimal calcWithdrawFee(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        if (a.compareTo(WITHDRAW_FEE_THRESHOLD) < 0) {
            return SMALL_WITHDRAW_FEE.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /** 审核通过后实际打款金额（提现金额 − 手续费） */
    public static BigDecimal calcWithdrawPayout(BigDecimal amount) {
        BigDecimal a = amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
        return a.subtract(calcWithdrawFee(a)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeAndValidateWithdrawAmount(BigDecimal raw) {
        BigDecimal amount = raw.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            throw new IllegalArgumentException("单笔提现最低 ¥1.00");
        }
        // 步进 0.1：金额（分）必须是 10 的倍数
        BigDecimal fen = amount.movePointRight(2);
        if (fen.remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("提现金额须为 0.1 的整数倍（如 1.0、1.1、1.2）");
        }
        return amount;
    }

    public List<WalletWithdrawRequest> listPendingForAdmin(String admin) {
        if (admin == null || !adminPolicy.isAdmin(admin.trim())) {
            throw new IllegalStateException("仅管理员可查看待审核提现");
        }
        return walletWithdrawRequestRepository.findTop100ByStatusOrderByCreatedAtAsc(WithdrawStatus.PENDING);
    }

    @Transactional
    public WalletWithdrawRequest auditWithdraw(Long requestId, WalletAuditRequest request, String admin) {
        if (admin == null || !adminPolicy.isAdmin(admin.trim())) {
            throw new IllegalStateException("仅管理员可审核提现");
        }

        WalletWithdrawRequest wr = walletWithdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("提现申请不存在"));
        if (wr.getStatus() != WithdrawStatus.PENDING) {
            throw new IllegalStateException("该提现申请已处理");
        }

        WalletAccount w = getOrCreate(wr.getUsername());
        BigDecimal amount = wr.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (request != null && request.isApprove()) {
            boolean alipaySandbox = isAlipaySandboxWithdraw(wr);
            if (alipaySandbox) {
                AlipayService alipayService = alipayServiceProvider.getIfAvailable();
                if (alipayService == null) {
                    throw new IllegalStateException("支付宝服务未就绪，无法完成沙箱转账");
                }
                String outBizNo = alipayService.transferForApprovedWithdraw(wr);
                BigDecimal fee = calcWithdrawFee(amount);
                BigDecimal payout = calcWithdrawPayout(amount);
                wr.setRemark(ALIPAY_SANDBOX_WITHDRAW_REMARK + " #" + outBizNo
                        + " | 手续费¥" + fee.toPlainString()
                        + "，实付¥" + payout.toPlainString());
            }

            wr.setStatus(WithdrawStatus.APPROVED);
            w.setWithdrawingAmount(w.getWithdrawingAmount().subtract(amount));
            w.setTotalWithdrawn(w.getTotalWithdrawn().add(amount));

            WalletTransaction t = new WalletTransaction();
            t.setUsername(wr.getUsername());
            t.setType(WalletTransactionType.WITHDRAW_APPROVE);
            t.setAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            t.setBalanceAfter(w.getBalance());
            t.setWithdrawRequestId(wr.getId());
            t.setRemark(alipaySandbox ? "支付宝沙箱提现审核通过" : "提现审核通过");
            walletTransactionRepository.save(t);

            remoteAuditLog.log(admin, "WALLET_WITHDRAW_APPROVED", null, "通过提现申请#" + wr.getId());
            RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
            if (systemMessages != null) {
                systemMessages.notifyWithdrawApproved(wr.getUsername(), amount, wr.getId(), alipaySandbox);
            }
        } else {
            wr.setStatus(WithdrawStatus.REJECTED);
            w.setWithdrawingAmount(w.getWithdrawingAmount().subtract(amount));
            w.setBalance(w.getBalance().add(amount));

            WalletTransaction t = new WalletTransaction();
            t.setUsername(wr.getUsername());
            t.setType(WalletTransactionType.WITHDRAW_REJECT);
            t.setAmount(amount);
            t.setBalanceAfter(w.getBalance());
            t.setWithdrawRequestId(wr.getId());
            t.setRemark("提现审核驳回，金额已退回");
            walletTransactionRepository.save(t);

            remoteAuditLog.log(admin, "WALLET_WITHDRAW_REJECTED", null, "驳回提现申请#" + wr.getId());
            RemoteSystemMessageSupport systemMessages = systemMessageSupport.getIfAvailable();
            if (systemMessages != null) {
                String remark = request == null ? "" : request.getRemark();
                systemMessages.notifyWithdrawRejected(wr.getUsername(), amount, wr.getId(), remark);
            }
        }

        wr.setAuditedBy(admin);
        wr.setAuditRemark(request == null ? "" : (request.getRemark() == null ? "" : request.getRemark().trim()));
        wr.setAuditedAt(LocalDateTime.now());

        walletAccountRepository.save(w);
        return walletWithdrawRequestRepository.save(wr);
    }

    @Transactional
    public int backfillCompletedTaskIncome(String username) {
        String user = requireUser(username);
        int count = 0;
        var allTasks = deliveryTaskRepository.findAll();
        for (DeliveryTask t : allTasks) {
            if (t.getStatus() != TaskStatus.COMPLETED) continue;
            if (t.getClaimerName() == null || !t.getClaimerName().trim().equalsIgnoreCase(user)) continue;
            if (t.getReward() == null || t.getReward().signum() <= 0) continue;
            incomeFromTask(user, t.getReward(), t.getId(), t.getTitle());
            count++;
        }
        return count;
    }

    public Map<String, Object> snapshot(String username) {
        // 打开钱包时合并历史重复账户，保证支付宝充值与管理员充值余额累加
        walletAccountSupport.getOrCreateMerged(username);
        backfillCompletedTaskIncome(username);
        WalletOverviewDTO o = overview(username);
        return Map.of(
                "overview", o,
                "transactions", myTransactions(username),
                "withdrawRequests", myWithdraws(username)
        );
    }

    @Transactional
    public Map<String, Object> forceRebuild(String username) {
        walletAccountSupport.getOrCreateMerged(username);
        int scanned = backfillCompletedTaskIncome(username);
        WalletOverviewDTO o = overview(username);
        return Map.of(
                "message", "钱包已重建合并",
                "scannedTasks", scanned,
                "overview", o,
                "transactions", myTransactions(username),
                "withdrawRequests", myWithdraws(username)
        );
    }
}
