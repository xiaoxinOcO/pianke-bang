package com.campus.controller;

import com.campus.dto.AlipayRechargeRequest;
import com.campus.dto.AlipayWithdrawRequest;
import com.campus.security.AuthContext;
import com.campus.service.AlipayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet/alipay")
public class AlipayController {

    private final AlipayService alipayService;

    public AlipayController(AlipayService alipayService) {
        this.alipayService = alipayService;
    }

    @GetMapping("/key-setup")
    public Map<String, Object> keySetup() {
        return alipayService.keySetupHint();
    }

    @GetMapping("/pending")
    public Map<String, Object> pending() {
        return alipayService.latestPendingOrder(AuthContext.getCurrentUser());
    }

    @PostMapping("/recharge")
    public Map<String, Object> createRecharge(@RequestBody AlipayRechargeRequest request) {
        return alipayService.createRechargeOrder(AuthContext.getCurrentUser(), request);
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody AlipayWithdrawRequest request) {
        return alipayService.applySandboxWithdraw(AuthContext.getCurrentUser(), request);
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        return alipayService.handleNotify(params);
    }

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returnPage(HttpServletRequest request) {
        String outTradeNo = firstParam(request, "out_trade_no");
        return alipayService.buildReturnRedirectHtml(outTradeNo);
    }

    @PostMapping("/sync-latest")
    public Map<String, Object> syncLatest() {
        return alipayService.syncLatestPendingOrder(AuthContext.getCurrentUser());
    }

    @PostMapping("/task-pay")
    public Map<String, Object> createTaskPay(@RequestBody Map<String, Object> body) {
        Object taskIdRaw = body == null ? null : body.get("taskId");
        if (taskIdRaw == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        Long taskId = Long.parseLong(String.valueOf(taskIdRaw));
        Long userCouponId = null;
        Object couponRaw = body.get("userCouponId");
        if (couponRaw != null && !String.valueOf(couponRaw).isBlank()) {
            userCouponId = Long.parseLong(String.valueOf(couponRaw));
        }
        return alipayService.createTaskBountyPayOrder(AuthContext.getCurrentUser(), taskId, userCouponId);
    }

    @PostMapping("/task-sync/{taskId}")
    public Map<String, Object> syncTask(@PathVariable("taskId") Long taskId) {
        return alipayService.syncTaskBountyOrder(AuthContext.getCurrentUser(), taskId);
    }

    @PostMapping("/task-sync-latest")
    public Map<String, Object> syncLatestTask() {
        return alipayService.syncLatestTaskBountyOrder(AuthContext.getCurrentUser());
    }

    @PostMapping("/sync/{outTradeNo}")
    public Map<String, Object> sync(@PathVariable("outTradeNo") String outTradeNo) {
        return alipayService.syncOrder(AuthContext.getCurrentUser(), outTradeNo);
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private String firstParam(HttpServletRequest request, String name) {
        String[] values = request.getParameterMap().get(name);
        if (values == null || values.length == 0) {
            return "";
        }
        return values[0] == null ? "" : values[0];
    }
}
