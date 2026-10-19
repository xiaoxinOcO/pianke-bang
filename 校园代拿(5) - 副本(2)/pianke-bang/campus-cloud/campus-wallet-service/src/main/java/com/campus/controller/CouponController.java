package com.campus.controller;

import com.campus.dto.CouponPreviewRequest;
import com.campus.dto.CouponRedeemRequest;
import com.campus.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 活动位（未登录也可看） */
    @GetMapping("/activities")
    public List<Map<String, Object>> activities() {
        return couponService.activities();
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@RequestParam(value = "status", required = false) String status) {
        return couponService.mine(status);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody CouponPreviewRequest request) {
        return couponService.preview(request);
    }

    @PostMapping("/claim-newcomer")
    public Map<String, Object> claimNewcomer() {
        return couponService.claimNewcomer();
    }

    @PostMapping("/claim/{templateId}")
    public Map<String, Object> claimTemplate(@PathVariable("templateId") Long templateId) {
        return couponService.claimTemplate(templateId);
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody CouponRedeemRequest request) {
        return couponService.redeem(request);
    }
}
