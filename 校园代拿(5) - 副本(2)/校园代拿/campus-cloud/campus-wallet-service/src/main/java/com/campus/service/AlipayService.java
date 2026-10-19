package com.campus.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.campus.client.TaskInternalClient;
import com.campus.config.AlipayProperties;
import com.campus.dto.AlipayRechargeRequest;
import com.campus.dto.AlipayWithdrawRequest;
import com.campus.model.WalletWithdrawRequest;
import com.campus.model.AlipayOrderType;
import com.campus.model.AlipayRechargeOrder;
import com.campus.model.AlipayRechargeStatus;
import com.campus.repository.AlipayRechargeOrderRepository;
import com.campus.support.WalletAccountSupport;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AlipayService {

    private static final Logger log = LoggerFactory.getLogger(AlipayService.class);

    private static final BigDecimal MAX_RECHARGE = new BigDecimal("10000.00");
    private static final BigDecimal MAX_WITHDRAW = new BigDecimal("10000.00");
    private static final String SANDBOX_PAYEE_NAME = "沙箱环境";
    private static final Pattern OUT_TRADE_NO_PATTERN = Pattern.compile("((?:WR|WT)\\d{10,})");

    private final AlipayProperties properties;
    private final AlipayRechargeOrderRepository orderRepository;
    private final WalletService walletService;
    private final TaskInternalClient taskInternalClient;
    private final WalletAccountSupport walletAccountSupport;

    public AlipayService(
            AlipayProperties properties,
            AlipayRechargeOrderRepository orderRepository,
            WalletService walletService,
            TaskInternalClient taskInternalClient,
            WalletAccountSupport walletAccountSupport
    ) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.walletService = walletService;
        this.taskInternalClient = taskInternalClient;
        this.walletAccountSupport = walletAccountSupport;
    }

    public Map<String, Object> keySetupHint() {
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("appId", properties.getAppId());
        hint.put("signType", properties.getSignType());
        hint.put("appPublicKeyToUpload", properties.getAppPublicKey());
        hint.put("alipayPublicKeyInConfig", properties.getAlipayPublicKey());
        hint.put("message", "invalid-signature 通常是开放平台未上传应用公钥，或上传的公钥与 private-key 不匹配。"
                + " 请将 appPublicKeyToUpload 完整粘贴到沙箱应用 9021000164617524 的「接口加签方式 → 应用公钥」，"
                + "保存后再把页面显示的「支付宝公钥」更新到 alipay-public-key。");
        return hint;
    }

    public Map<String, Object> createRechargeOrder(String username, AlipayRechargeRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("支付宝充值未启用");
        }
        if (properties.getPrivateKey().isBlank() || properties.getAppPublicKey().isBlank()) {
            throw new IllegalStateException("支付宝密钥未配置完整，请联系管理员检查 campus.alipay 配置");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("充值金额不能为空");
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        if (amount.compareTo(MAX_RECHARGE) > 0) {
            throw new IllegalArgumentException("单次充值金额不能超过 10000 元");
        }

        String outTradeNo = generateOutTradeNo();
        AlipayRechargeOrder order = new AlipayRechargeOrder();
        order.setOutTradeNo(outTradeNo);
        order.setUsername(requireUsername(username));
        order.setAmount(amount);
        order.setStatus(AlipayRechargeStatus.PENDING);
        order.setOrderType(AlipayOrderType.RECHARGE);
        orderRepository.save(order);

        String payForm = buildPagePayForm(
                outTradeNo,
                amount,
                "校园代拿钱包充值",
                "校园代拿平台钱包余额充值（沙箱）"
        );
        return Map.of(
                "outTradeNo", outTradeNo,
                "amount", amount,
                "payForm", payForm
        );
    }

    /**
     * 提交支付宝沙箱提现申请（待管理员审核，审核通过后再转账）。
     */
    @Transactional
    public Map<String, Object> applySandboxWithdraw(String username, AlipayWithdrawRequest request) {
        ensureAlipayReady(username);
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("提现金额不能为空");
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(WalletService.MIN_WITHDRAW_AMOUNT) < 0) {
            throw new IllegalArgumentException("单笔提现最低 ¥1.00");
        }
        BigDecimal fen = amount.movePointRight(2);
        if (fen.remainder(BigDecimal.TEN).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("提现金额须为 0.1 的整数倍（如 1.0、1.1、1.2）");
        }
        if (amount.compareTo(MAX_WITHDRAW) > 0) {
            throw new IllegalArgumentException("单次提现金额不能超过 10000 元");
        }

        String alipayAccount = request.getAlipayAccount() == null ? "" : request.getAlipayAccount().trim();
        if (alipayAccount.isBlank()) {
            throw new IllegalArgumentException("请填写沙箱支付宝账号（买家登录号）");
        }

        String payeeName = request.getAccountName() == null ? "" : request.getAccountName().trim();
        if (payeeName.isBlank()) {
            payeeName = SANDBOX_PAYEE_NAME;
        }

        String user = requireUsername(username);
        WalletWithdrawRequest saved = walletService.applyAlipaySandboxWithdraw(
                user, amount, alipayAccount, payeeName);

        return Map.of(
                "message", "提现申请已提交，等待管理员审核",
                "requestId", saved.getId(),
                "amount", amount,
                "alipayAccount", alipayAccount,
                "accountName", payeeName,
                "status", saved.getStatus().name()
        );
    }

    /**
     * 管理员审核通过后，向沙箱支付宝账号发起单笔转账。
     */
    public String transferForApprovedWithdraw(WalletWithdrawRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("提现申请不存在");
        }
        if (!walletService.isAlipaySandboxWithdraw(request)) {
            throw new IllegalStateException("该提现申请不是支付宝沙箱提现");
        }
        ensureAlipayReady(request.getUsername());

        String alipayAccount = request.getAccountNo() == null ? "" : request.getAccountNo().trim();
        if (alipayAccount.isBlank()) {
            throw new IllegalArgumentException("提现申请缺少沙箱支付宝账号");
        }
        String payeeName = request.getAccountName() == null || request.getAccountName().isBlank()
                ? SANDBOX_PAYEE_NAME
                : request.getAccountName().trim();
        // 实际打款 = 申请金额 − 小额手续费
        BigDecimal payout = WalletService.calcWithdrawPayout(request.getAmount());
        if (payout.signum() <= 0) {
            throw new IllegalStateException("提现到账金额无效");
        }
        String outBizNo = generateWithdrawOutBizNo(request.getId());
        executeAlipayTransfer(outBizNo, payout, alipayAccount, payeeName);
        return outBizNo;
    }

    public Map<String, Object> createTaskBountyPayOrder(String username, Long taskId) {
        return createTaskBountyPayOrder(username, taskId, null);
    }

    public Map<String, Object> createTaskBountyPayOrder(String username, Long taskId, Long userCouponId) {
        ensureAlipayReady(username);
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        String user = requireUsername(username);
        Map<String, Object> prepareBody = new LinkedHashMap<>();
        prepareBody.put("username", user);
        if (userCouponId != null) {
            prepareBody.put("userCouponId", userCouponId);
        }
        Map<String, Object> taskInfo = taskInternalClient.prepareAlipayBounty(taskId, prepareBody);
        BigDecimal amount = toBigDecimal(taskInfo.get("amount"));
        String title = String.valueOf(taskInfo.getOrDefault("title", "任务赏金"));

        Optional<AlipayRechargeOrder> existingPending = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PENDING);
        if (existingPending.isPresent()
                && user.equalsIgnoreCase(existingPending.get().getUsername())
                && existingPending.get().getAmount() != null
                && existingPending.get().getAmount().compareTo(amount) == 0) {
            AlipayRechargeOrder order = existingPending.get();
            String payForm = buildPagePayForm(
                    order.getOutTradeNo(),
                    order.getAmount(),
                    "校园代拿任务赏金",
                    "发布任务赏金支付：" + title
            );
            return Map.of(
                    "outTradeNo", order.getOutTradeNo(),
                    "taskId", taskId,
                    "amount", order.getAmount(),
                    "payForm", payForm
            );
        }
        if (existingPending.isPresent()
                && user.equalsIgnoreCase(existingPending.get().getUsername())) {
            AlipayRechargeOrder stale = existingPending.get();
            stale.setStatus(AlipayRechargeStatus.CLOSED);
            orderRepository.save(stale);
        }

        String outTradeNo = generateTaskOutTradeNo();
        AlipayRechargeOrder order = new AlipayRechargeOrder();
        order.setOutTradeNo(outTradeNo);
        order.setUsername(user);
        order.setAmount(amount);
        order.setStatus(AlipayRechargeStatus.PENDING);
        order.setOrderType(AlipayOrderType.TASK_BOUNTY);
        order.setTaskId(taskId);
        orderRepository.save(order);

        String payForm = buildPagePayForm(
                outTradeNo,
                amount,
                "校园代拿任务赏金",
                "发布任务赏金支付：" + title
        );
        return Map.of(
                "outTradeNo", outTradeNo,
                "taskId", taskId,
                "amount", amount,
                "payForm", payForm
        );
    }

    @Transactional
    public Map<String, Object> syncTaskBountyOrder(String username, Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        String user = requireUsername(username);
        AlipayRechargeOrder order = orderRepository
                .findFirstByTaskIdAndOrderTypeOrderByCreatedAtDesc(taskId, AlipayOrderType.TASK_BOUNTY)
                .filter(o -> o.getUsername().equals(user))
                .orElseThrow(() -> new IllegalArgumentException("未找到该任务的支付宝订单"));
        return syncExistingOrder(order);
    }

    @Transactional
    public Map<String, Object> syncLatestTaskBountyOrder(String username) {
        String user = requireUsername(username);
        AlipayRechargeOrder order = orderRepository
                .findFirstByUsernameAndStatusAndOrderTypeOrderByCreatedAtDesc(
                        user, AlipayRechargeStatus.PENDING, AlipayOrderType.TASK_BOUNTY)
                .orElseThrow(() -> new IllegalArgumentException("暂无待同步的任务赏金支付订单"));
        return syncExistingOrder(order);
    }

    /**
     * 任务赏金若经支付宝支付，则原路退回沙箱买家账号，不记入平台钱包。
     *
     * @return true 表示已走支付宝退款；false 表示无已支付的支付宝赏金订单，应走钱包退还
     */
    @Transactional
    public boolean tryRefundTaskBountyToAlipay(String publisherName, Long taskId, BigDecimal amount, String remark) {
        if (taskId == null || amount == null || amount.signum() <= 0) {
            return false;
        }
        if (!properties.isEnabled()) {
            return false;
        }
        String user = requireUsername(publisherName);

        Optional<AlipayRechargeOrder> alreadyRefunded = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByPaidAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.REFUNDED);
        if (alreadyRefunded.isPresent() && user.equalsIgnoreCase(alreadyRefunded.get().getUsername())) {
            log.info("任务赏金支付宝订单已退款 taskId={}", taskId);
            return true;
        }

        // 沙箱支付后异步回调可能未到：退款前先向支付宝查单并落库
        syncPendingTaskBountyPaymentIfNeeded(user, taskId);

        Optional<AlipayRechargeOrder> paidOrder = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByPaidAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PAID)
                .or(() -> orderRepository.findFirstByTaskIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PAID));
        if (paidOrder.isEmpty() || !user.equalsIgnoreCase(paidOrder.get().getUsername())) {
            log.warn("未找到已支付的任务赏金支付宝订单，无法原路退款 taskId={}, publisher={}", taskId, user);
            return false;
        }

        AlipayRechargeOrder order = paidOrder.get();
        BigDecimal refundAmount = order.getAmount() != null ? order.getAmount() : amount;
        refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);
        String reason = remark == null || remark.isBlank() ? "任务取消，赏金原路退回" : remark.trim();
        log.info("发起支付宝赏金原路退款 taskId={}, outTradeNo={}, amount={}", taskId, order.getOutTradeNo(), refundAmount);
        executeAlipayRefund(order, refundAmount, reason);
        order.setStatus(AlipayRechargeStatus.REFUNDED);
        orderRepository.save(order);
        log.info("支付宝赏金原路退款成功 taskId={}, outTradeNo={}", taskId, order.getOutTradeNo());
        return true;
    }

    /** 是否存在 PENDING/PAID 且未退款的任务赏金支付宝订单 */
    public boolean hasUnsettledTaskBountyAlipayOrder(String publisherName, Long taskId) {
        if (taskId == null || publisherName == null || publisherName.isBlank()) {
            return false;
        }
        String user = requireUsername(publisherName);
        return orderRepository.findFirstByTaskIdAndOrderTypeOrderByCreatedAtDesc(taskId, AlipayOrderType.TASK_BOUNTY)
                .filter(order -> user.equalsIgnoreCase(order.getUsername()))
                .map(order -> order.getStatus() == AlipayRechargeStatus.PENDING
                        || order.getStatus() == AlipayRechargeStatus.PAID)
                .orElse(false);
    }

    private void syncPendingTaskBountyPaymentIfNeeded(String user, Long taskId) {
        Optional<AlipayRechargeOrder> pending = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PENDING);
        if (pending.isEmpty() || !user.equalsIgnoreCase(pending.get().getUsername())) {
            return;
        }
        AlipayRechargeOrder order = pending.get();
        AlipayTradeQueryResponse response = queryAlipayOrder(order.getOutTradeNo());
        if (response != null && response.isSuccess() && isPaidStatus(response.getTradeStatus())) {
            log.info("退款前同步支付宝查单成功，标记已支付 taskId={}, outTradeNo={}", taskId, order.getOutTradeNo());
            markOrderPaidWithoutTaskSideEffect(order.getOutTradeNo(), response.getTradeNo());
        }
    }

    private void markOrderPaidWithoutTaskSideEffect(String outTradeNo, String alipayTradeNo) {
        AlipayRechargeOrder order = orderRepository.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new IllegalArgumentException("充值订单不存在"));
        if (order.getStatus() == AlipayRechargeStatus.PAID || order.getStatus() == AlipayRechargeStatus.REFUNDED) {
            return;
        }
        order.setStatus(AlipayRechargeStatus.PAID);
        order.setAlipayTradeNo(alipayTradeNo);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional
    public String handleNotify(Map<String, String> params) {
        if (!verifySignature(params)) {
            return "failure";
        }

        String outTradeNo = normalizeOutTradeNo(params.get("out_trade_no"));
        String tradeStatus = params.get("trade_status");
        String alipayTradeNo = params.get("trade_no");

        if (outTradeNo == null || outTradeNo.isBlank()) {
            return "failure";
        }
        if (!isPaidStatus(tradeStatus)) {
            return "success";
        }

        markOrderPaid(outTradeNo, alipayTradeNo);
        return "success";
    }

    @Transactional
    public Map<String, Object> syncOrder(String username, String outTradeNo) {
        String normalized = normalizeOutTradeNo(outTradeNo);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }

        AlipayRechargeOrder order = orderRepository.findByOutTradeNo(normalized)
                .orElseThrow(() -> new IllegalArgumentException("充值订单不存在：" + normalized));

        String user = username == null ? "" : username.trim().toLowerCase();
        if (!order.getUsername().equals(user)) {
            throw new IllegalStateException("无权查询该订单");
        }

        return syncExistingOrder(order);
    }

    public Map<String, Object> latestPendingOrder(String username) {
        String user = requireUsername(username);
        return orderRepository
                .findFirstByUsernameAndStatusAndOrderTypeOrderByCreatedAtDesc(
                        user, AlipayRechargeStatus.PENDING, AlipayOrderType.RECHARGE)
                .map(order -> orderSnapshot(order, "待同步充值订单"))
                .orElseGet(() -> Map.of("message", "暂无待同步订单"));
    }

    @Transactional
    public Map<String, Object> syncLatestPendingOrder(String username) {
        String user = requireUsername(username);
        AlipayRechargeOrder order = orderRepository
                .findFirstByUsernameAndStatusAndOrderTypeOrderByCreatedAtDesc(
                        user, AlipayRechargeStatus.PENDING, AlipayOrderType.RECHARGE)
                .orElseThrow(() -> new IllegalArgumentException("暂无待同步的充值订单，请先发起充值"));

        return syncExistingOrder(order);
    }

    public String buildReturnRedirectHtml(String rawOutTradeNo) {
        String outTradeNo = normalizeOutTradeNo(rawOutTradeNo);
        AlipayRechargeOrder order = outTradeNo.isBlank()
                ? null
                : orderRepository.findByOutTradeNo(outTradeNo).orElse(null);

        String frontendUrl = properties.getFrontendReturnUrl();
        if (order != null && order.getOrderType() == AlipayOrderType.TASK_BOUNTY) {
            frontendUrl = properties.getFrontendTaskReturnUrl();
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            frontendUrl = order != null && order.getOrderType() == AlipayOrderType.TASK_BOUNTY
                    ? "http://localhost:5173/#/publish"
                    : "http://localhost:5173/#/wallet";
        }

        Long taskId = order == null ? null : order.getTaskId();
        String taskIdJs = taskId == null ? "" : String.valueOf(taskId);
        String orderTypeJs = order == null || order.getOrderType() == null
                ? ""
                : order.getOrderType().name();
        String redirectHint = order != null && order.getOrderType() == AlipayOrderType.TASK_BOUNTY
                ? "支付已完成，正在返回…"
                : "支付已完成，正在返回…";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>支付完成</title>
                </head>
                <body>
                  <p id="hint">%s</p>
                  <script>
                    (function () {
                      var order = '%s';
                      var taskId = '%s';
                      var orderType = '%s';
                      var frontendUrl = '%s';
                      var hintEl = document.getElementById('hint');

                      function applyLocalSyncFlags() {
                        if (order) {
                          sessionStorage.setItem('campus_pending_alipay_out_trade_no', order);
                          sessionStorage.setItem('campus_alipay_auto_sync', '1');
                        }
                        if (taskId) {
                          sessionStorage.setItem('campus_pending_alipay_task_id', taskId);
                          sessionStorage.setItem('campus_alipay_task_auto_sync', '1');
                        }
                      }

                      if (window.opener && !window.opener.closed) {
                        try {
                          window.opener.postMessage({
                            type: 'campus-alipay-return',
                            order: order,
                            taskId: taskId,
                            orderType: orderType
                          }, window.location.origin);
                          window.opener.focus();
                        } catch (e) {}
                        if (hintEl) {
                          hintEl.textContent = '支付完成，请返回原窗口继续操作…';
                        }
                        setTimeout(function () { window.close(); }, 500);
                        return;
                      }

                      applyLocalSyncFlags();
                      window.location.replace(frontendUrl);
                    })();
                  </script>
                </body>
                </html>
                """.formatted(
                redirectHint,
                escapeJs(outTradeNo),
                escapeJs(taskIdJs),
                escapeJs(orderTypeJs),
                escapeJs(frontendUrl)
        );
    }

    private Map<String, Object> syncExistingOrder(AlipayRechargeOrder order) {
        boolean taskOrder = order.getOrderType() == AlipayOrderType.TASK_BOUNTY;
        if (order.getStatus() == AlipayRechargeStatus.PAID) {
            return orderSnapshot(order, taskOrder ? "任务赏金已支付" : "订单已支付，余额已入账");
        }
        if (order.getStatus() == AlipayRechargeStatus.CLOSED
                || order.getStatus() == AlipayRechargeStatus.REFUNDED) {
            return orderSnapshot(order, "该订单已结束，无需同步");
        }

        AlipayTradeQueryResponse response = queryAlipayOrder(order.getOutTradeNo());
        if (response != null && response.isSuccess() && isPaidStatus(response.getTradeStatus())) {
            markOrderPaid(order.getOutTradeNo(), response.getTradeNo());
            AlipayRechargeOrder latest = orderRepository.findByOutTradeNo(order.getOutTradeNo()).orElse(order);
            return orderSnapshot(latest, taskOrder ? "任务赏金支付成功，请等待管理员审核" : "支付成功，余额已更新");
        }

        String subCode = response == null ? "" : nullToEmpty(response.getSubCode());
        String subMsg = response == null ? "" : nullToEmpty(response.getSubMsg());
        // 从未真正下单成功 / 沙箱已失效：关闭本地待同步，避免前端反复提醒
        if ("ACQ.TRADE_NOT_EXIST".equals(subCode)
                || subMsg.contains("交易不存在")
                || subMsg.contains("账单不存在")) {
            order.setStatus(AlipayRechargeStatus.CLOSED);
            orderRepository.save(order);
            return orderSnapshot(order, "该充值未完成或已失效，已清除待同步状态");
        }

        String message = subMsg.isBlank()
                ? "订单尚未支付，请确认支付宝沙箱是否扣款成功"
                : "订单尚未支付：" + subMsg;
        return orderSnapshot(order, message);
    }

    private void ensureAlipayReady(String username) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("支付宝支付未启用");
        }
        if (properties.getPrivateKey().isBlank() || properties.getAppPublicKey().isBlank()) {
            throw new IllegalStateException("支付宝密钥未配置完整，请联系管理员检查 campus.alipay 配置");
        }
        requireUsername(username);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private String requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户未登录");
        }
        return walletAccountSupport.resolveCanonicalUsername(username);
    }

    private String normalizeOutTradeNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.contains(",")) {
            trimmed = trimmed.split(",")[0].trim();
        }
        Matcher matcher = OUT_TRADE_NO_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return trimmed;
    }

    private String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void markOrderPaid(String outTradeNo, String alipayTradeNo) {
        AlipayRechargeOrder order = orderRepository.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new IllegalArgumentException("充值订单不存在"));

        if (order.getStatus() == AlipayRechargeStatus.PAID) {
            return;
        }

        order.setStatus(AlipayRechargeStatus.PAID);
        order.setAlipayTradeNo(alipayTradeNo);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        if (order.getOrderType() == AlipayOrderType.TASK_BOUNTY) {
            if (order.getTaskId() == null) {
                throw new IllegalStateException("任务赏金订单缺少任务ID");
            }
            taskInternalClient.confirmAlipayBounty(order.getTaskId(), Map.of(
                    "username", order.getUsername(),
                    "outTradeNo", outTradeNo,
                    "amount", order.getAmount()
            ));
            return;
        }

        walletService.creditFromAlipayRecharge(order.getUsername(), order.getAmount(), outTradeNo);
    }

    private Map<String, Object> orderSnapshot(AlipayRechargeOrder order, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("outTradeNo", order.getOutTradeNo());
        result.put("amount", order.getAmount());
        result.put("status", order.getStatus().name());
        result.put("orderType", order.getOrderType().name());
        result.put("taskId", order.getTaskId());
        result.put("paidAt", order.getPaidAt());
        return result;
    }

    private String buildPagePayForm(String outTradeNo, BigDecimal amount, String subject, String body) {
        try {
            AlipayClient client = createClient();
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(properties.getNotifyUrl());
            request.setReturnUrl(properties.getReturnUrl());
            request.setBizContent(
                    "{\"out_trade_no\":\"" + outTradeNo + "\","
                            + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\","
                            + "\"total_amount\":\"" + amount.toPlainString() + "\","
                            + "\"subject\":\"" + escapeJson(subject) + "\","
                            + "\"body\":\"" + escapeJson(body) + "\"}"
            );

            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (response == null || response.getBody() == null || response.getBody().isBlank()) {
                throw new IllegalStateException("支付宝未返回支付表单");
            }
            return response.getBody();
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("创建支付宝订单失败：" + ex.getErrMsg(), ex);
        }
    }

    private AlipayTradeQueryResponse queryAlipayOrder(String outTradeNo) {
        try {
            AlipayClient client = createClient();
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizContent("{\"out_trade_no\":\"" + outTradeNo + "\"}");
            return client.execute(request);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("查询支付宝订单失败：" + ex.getErrMsg(), ex);
        }
    }

    private void executeAlipayTransfer(String outBizNo, BigDecimal amount, String alipayAccount, String payeeName) {
        try {
            AlipayClient client = createClient();
            AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
            request.setBizContent(
                    "{\"out_biz_no\":\"" + outBizNo + "\","
                            + "\"trans_amount\":\"" + amount.toPlainString() + "\","
                            + "\"product_code\":\"TRANS_ACCOUNT_NO_PWD\","
                            + "\"biz_scene\":\"DIRECT_TRANSFER\","
                            + "\"order_title\":\"片刻帮接单员提现\","
                            + "\"remark\":\"校园代拿钱包提现（沙箱）\","
                            + "\"payee_info\":{"
                            + "\"identity\":\"" + escapeJson(alipayAccount) + "\","
                            + "\"identity_type\":\"ALIPAY_LOGON_ID\","
                            + "\"name\":\"" + escapeJson(payeeName) + "\""
                            + "}"
                            + "}"
            );
            AlipayFundTransUniTransferResponse response = client.execute(request);
            if (response == null || !response.isSuccess()) {
                String subMsg = response == null ? "无响应" : response.getSubMsg();
                throw new IllegalStateException("支付宝转账失败：" + subMsg);
            }
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝转账失败：" + ex.getErrMsg(), ex);
        }
    }

    private void executeAlipayRefund(AlipayRechargeOrder order, BigDecimal refundAmount, String reason) {
        try {
            AlipayClient client = createClient();
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            String outRequestNo = generateRefundOutRequestNo();
            request.setBizContent(
                    "{\"out_trade_no\":\"" + order.getOutTradeNo() + "\","
                            + "\"refund_amount\":\"" + refundAmount.toPlainString() + "\","
                            + "\"refund_reason\":\"" + escapeJson(reason) + "\","
                            + "\"out_request_no\":\"" + outRequestNo + "\"}"
            );
            AlipayTradeRefundResponse response = client.execute(request);
            if (response != null && response.isSuccess()) {
                return;
            }
            String subCode = response == null ? "" : nullToEmpty(response.getSubCode());
            if (isIdempotentRefundFailure(subCode)) {
                log.warn("支付宝订单无法再次退款，视为已处理 outTradeNo={}, subCode={}, subMsg={}",
                        order.getOutTradeNo(), subCode, response == null ? "" : nullToEmpty(response.getSubMsg()));
                return;
            }
            String subMsg = response == null ? "无响应" : response.getSubMsg();
            throw new IllegalStateException("支付宝退款失败：" + subMsg);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝退款失败：" + ex.getErrMsg(), ex);
        }
    }

    /** 沙箱/重复退款：订单已关闭、不存在或已全额退款，无需再调 API */
    private static boolean isIdempotentRefundFailure(String subCode) {
        return "ACQ.TRADE_HAS_CLOSE".equals(subCode)
                || "ACQ.TRADE_NOT_EXIST".equals(subCode)
                || "ACQ.TRADE_HAS_FINISHED".equals(subCode);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean verifySignature(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    properties.getCharset(),
                    properties.getSignType()
            );
        } catch (AlipayApiException ex) {
            return false;
        }
    }

    private boolean isPaidStatus(String tradeStatus) {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }

    private AlipayClient createClient() {
        try {
            AlipayConfig config = new AlipayConfig();
            config.setServerUrl(properties.getGatewayUrl());
            config.setAppId(properties.getAppId());
            config.setPrivateKey(properties.getPrivateKey());
            config.setFormat(properties.getFormat());
            config.setCharset(properties.getCharset());
            config.setAlipayPublicKey(properties.getAlipayPublicKey());
            config.setSignType(properties.getSignType());
            config.setConnectTimeout(15000);
            config.setReadTimeout(60000);
            return new DefaultAlipayClient(config);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("初始化支付宝客户端失败：" + ex.getErrMsg(), ex);
        }
    }

    private String generateOutTradeNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "WR" + time + suffix;
    }

    private String generateTaskOutTradeNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "WT" + time + suffix;
    }

    private String generateRefundOutRequestNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "WRF" + time + suffix;
    }

    private String generateWithdrawOutBizNo(Long requestId) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        long id = requestId == null ? 0L : requestId;
        return "WW" + id + time;
    }

    private String generateWithdrawOutBizNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "WW" + time + suffix;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
