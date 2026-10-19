package com.campus.support;

import com.campus.model.UserAccount;
import com.campus.model.WalletAccount;
import com.campus.model.WalletTransaction;
import com.campus.model.WalletTransactionType;
import com.campus.repository.UserAccountRepository;
import com.campus.repository.WalletAccountRepository;
import com.campus.repository.WalletTransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 钱包赏金写库逻辑。task-service 与 wallet-service 共用 campus_delivery 时，
 * 在同一本地事务内完成扣款/退款，避免 Seata 对同一 JDBC 资源重复注册分支。
 */
@Component
public class WalletBountyCore {

    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserAccountRepository userAccountRepository;
    private final WalletAccountSupport walletAccountSupport;

    public WalletBountyCore(
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            UserAccountRepository userAccountRepository,
            WalletAccountSupport walletAccountSupport
    ) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userAccountRepository = userAccountRepository;
        this.walletAccountSupport = walletAccountSupport;
    }

    public void lockTaskBounty(String publisherName, BigDecimal amount, Long taskId, String title) {
        String user = requireUser(publisherName);
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("赏金必须大于0");
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);

        if (walletTransactionRepository.existsByTaskIdAndUsernameAndType(taskId, user, WalletTransactionType.TASK_BOUNTY_LOCK)) {
            return;
        }

        WalletAccount w = getOrCreate(user);
        if (w.getBalance().compareTo(amt) < 0) {
            throw new IllegalStateException("钱包余额不足，无法支付赏金；请先通过接单完成任务获得收入后再发布");
        }

        w.setBalance(w.getBalance().subtract(amt));
        walletAccountRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.TASK_BOUNTY_LOCK);
        t.setAmount(amt.negate());
        t.setBalanceAfter(w.getBalance());
        t.setTaskId(taskId);
        t.setRemark("发布任务冻结赏金：" + (title == null ? "" : title));
        walletTransactionRepository.save(t);
    }

    public void refundTaskBountyToWallet(String publisherName, Long taskId, BigDecimal amount, String remark) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String user = requireUser(publisherName);
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);

        WalletAccount w = getOrCreate(user);
        w.setBalance(w.getBalance().add(amt));
        walletAccountRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.TASK_BOUNTY_REFUND);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setTaskId(taskId);
        t.setRemark(remark == null || remark.isBlank() ? "任务赏金退还" : remark.trim());
        walletTransactionRepository.save(t);
    }

    public void incomeFromTask(String username, BigDecimal amount, Long taskId, String title) {
        String user = requireUser(username);
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);

        if (taskId != null && walletTransactionRepository.existsByTaskIdAndUsernameAndType(
                taskId, user, WalletTransactionType.TASK_INCOME)) {
            return;
        }

        WalletAccount w = getOrCreate(user);
        w.setBalance(w.getBalance().add(amt));
        w.setTotalIncome(w.getTotalIncome().add(amt));
        walletAccountRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.TASK_INCOME);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setTaskId(taskId);
        t.setRemark("完成任务收入：" + (title == null ? "" : title));
        walletTransactionRepository.save(t);

        settlePendingPenaltyDebt(user, taskId, title);
    }

    /** 平台优惠券补贴记账（不改余额，仅审计流水） */
    public void recordCouponSubsidy(String publisherName, Long taskId, BigDecimal discount, String title) {
        if (discount == null || discount.signum() <= 0 || taskId == null) {
            return;
        }
        String user = requireUser(publisherName);
        BigDecimal amt = discount.setScale(2, RoundingMode.HALF_UP);
        if (walletTransactionRepository.existsByTaskIdAndUsernameAndType(
                taskId, user, WalletTransactionType.COUPON_SUBSIDY)) {
            return;
        }
        WalletAccount w = getOrCreate(user);
        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.COUPON_SUBSIDY);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setTaskId(taskId);
        t.setRemark("平台优惠券补贴：" + (title == null ? "" : title));
        walletTransactionRepository.save(t);
    }

    public void compensatePublisherForTimeout(
            String riderName,
            String publisherName,
            BigDecimal amount,
            Long taskId,
            String title
    ) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String rider = requireUser(riderName);
        String publisher = requireUser(publisherName);
        BigDecimal compensation = amount.setScale(2, RoundingMode.HALF_UP);

        WalletAccount riderWallet = getOrCreate(rider);
        BigDecimal available = riderWallet.getBalance().max(BigDecimal.ZERO);
        BigDecimal actualDeduct = available.min(compensation);
        BigDecimal debt = compensation.subtract(actualDeduct).setScale(2, RoundingMode.HALF_UP);

        if (actualDeduct.signum() > 0) {
            riderWallet.setBalance(riderWallet.getBalance().subtract(actualDeduct));
            walletAccountRepository.save(riderWallet);

            WalletTransaction debitTx = new WalletTransaction();
            debitTx.setUsername(rider);
            debitTx.setType(WalletTransactionType.TIMEOUT_COMPENSATION_DEBIT);
            debitTx.setAmount(actualDeduct.negate());
            debitTx.setBalanceAfter(riderWallet.getBalance());
            debitTx.setTaskId(taskId);
            debitTx.setRemark("超时赔付扣款：" + (title == null ? "" : title));
            walletTransactionRepository.save(debitTx);
        }

        if (debt.signum() > 0) {
            UserAccount riderUser = userAccountRepository.findByUsernameIgnoreCase(rider)
                    .orElseThrow(() -> new IllegalArgumentException("接单员不存在"));
            BigDecimal pending = riderUser.getPendingPenalty() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : riderUser.getPendingPenalty().setScale(2, RoundingMode.HALF_UP);
            riderUser.setPendingPenalty(pending.add(debt));
            userAccountRepository.save(riderUser);

            WalletTransaction debtTx = new WalletTransaction();
            debtTx.setUsername(rider);
            debtTx.setType(WalletTransactionType.TIMEOUT_COMPENSATION_DEBT);
            debtTx.setAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            debtTx.setBalanceAfter(riderWallet.getBalance());
            debtTx.setTaskId(taskId);
            debtTx.setRemark("超时赔付待扣欠款：" + debt);
            walletTransactionRepository.save(debtTx);
        }

        WalletAccount publisherWallet = getOrCreate(publisher);
        publisherWallet.setBalance(publisherWallet.getBalance().add(compensation));
        publisherWallet.setTotalIncome(publisherWallet.getTotalIncome().add(compensation));
        walletAccountRepository.save(publisherWallet);

        WalletTransaction creditTx = new WalletTransaction();
        creditTx.setUsername(publisher);
        creditTx.setType(WalletTransactionType.TIMEOUT_COMPENSATION_CREDIT);
        creditTx.setAmount(compensation);
        creditTx.setBalanceAfter(publisherWallet.getBalance());
        creditTx.setTaskId(taskId);
        creditTx.setRemark("超时赔付收入：" + (title == null ? "" : title));
        walletTransactionRepository.save(creditTx);
    }

    public void creditManualForDemo(String username, BigDecimal amount) {
        String user = requireUser(username);
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        WalletAccount w = getOrCreate(user);
        w.setBalance(w.getBalance().add(amt));
        w.setTotalIncome(w.getTotalIncome().add(amt));
        walletAccountRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUsername(user);
        t.setType(WalletTransactionType.MANUAL_BONUS);
        t.setAmount(amt);
        t.setBalanceAfter(w.getBalance());
        t.setRemark("演示数据充值");
        walletTransactionRepository.save(t);
    }

    private void settlePendingPenaltyDebt(String username, Long taskId, String title) {
        UserAccount riderUser = userAccountRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (riderUser == null) {
            return;
        }
        BigDecimal pending = riderUser.getPendingPenalty() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : riderUser.getPendingPenalty().setScale(2, RoundingMode.HALF_UP);
        if (pending.signum() <= 0) {
            return;
        }

        WalletAccount wallet = getOrCreate(username);
        BigDecimal available = wallet.getBalance().max(BigDecimal.ZERO);
        if (available.signum() <= 0) {
            return;
        }

        BigDecimal repay = pending.min(available).setScale(2, RoundingMode.HALF_UP);
        if (repay.signum() <= 0) {
            return;
        }

        wallet.setBalance(wallet.getBalance().subtract(repay));
        walletAccountRepository.save(wallet);

        riderUser.setPendingPenalty(pending.subtract(repay).setScale(2, RoundingMode.HALF_UP));
        userAccountRepository.save(riderUser);

        WalletTransaction repayTx = new WalletTransaction();
        repayTx.setUsername(username);
        repayTx.setType(WalletTransactionType.TIMEOUT_COMPENSATION_DEBT_REPAY);
        repayTx.setAmount(repay.negate());
        repayTx.setBalanceAfter(wallet.getBalance());
        repayTx.setTaskId(taskId);
        repayTx.setRemark("收入抵扣超时欠款：" + (title == null ? "" : title));
        walletTransactionRepository.save(repayTx);
    }

    private String requireUser(String username) {
        return walletAccountSupport.resolveCanonicalUsername(username);
    }

    private WalletAccount getOrCreate(String username) {
        return walletAccountSupport.getOrCreateMerged(username);
    }
}
