package com.campus.controller;

import com.campus.dto.CouponIssueRequest;
import com.campus.dto.CouponRedeemCodeCreateRequest;
import com.campus.dto.CouponTemplateCreateRequest;
import com.campus.model.CouponRedeemCode;
import com.campus.model.CouponTemplate;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import com.campus.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;
    private final AdminPolicy adminPolicy;

    public AdminCouponController(CouponService couponService, AdminPolicy adminPolicy) {
        this.couponService = couponService;
        this.adminPolicy = adminPolicy;
    }

    @GetMapping("/templates")
    public List<CouponTemplate> listTemplates() {
        requireAdmin();
        return couponService.listTemplates();
    }

    @PostMapping("/templates")
    public CouponTemplate createTemplate(@RequestBody CouponTemplateCreateRequest request) {
        requireAdmin();
        return couponService.createTemplate(request);
    }

    /** 上线 / 下线活动券模板 */
    @PatchMapping("/templates/{id}/status")
    public CouponTemplate updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body
    ) {
        requireAdmin();
        String status = body == null ? null : body.get("status");
        return couponService.updateTemplateStatus(id, status);
    }

    /** 更新活动海报 / 默认主题色 */
    @PatchMapping("/templates/{id}/banner")
    public CouponTemplate updateBanner(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body
    ) {
        requireAdmin();
        return couponService.updateTemplateBanner(id, body);
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deleteTemplate(@PathVariable("id") Long id) {
        requireAdmin();
        couponService.deleteTemplate(id);
        return Map.of("ok", true, "message", "模板已删除");
    }

    /** 定向发券：活动、投诉补偿、种子用户 */
    @PostMapping("/issue")
    public Map<String, Object> issue(@RequestBody CouponIssueRequest request) {
        requireAdmin();
        return couponService.issue(request);
    }

    @PostMapping("/redeem-codes")
    public Map<String, Object> createRedeemCodes(@RequestBody CouponRedeemCodeCreateRequest request) {
        requireAdmin();
        return couponService.createRedeemCodes(request);
    }

    @GetMapping("/redeem-codes")
    public List<CouponRedeemCode> listRedeemCodes(
            @RequestParam(value = "templateId", required = false) Long templateId
    ) {
        requireAdmin();
        return couponService.listRedeemCodes(templateId);
    }

    private void requireAdmin() {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || operator.isBlank() || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("无管理员权限");
        }
    }
}
