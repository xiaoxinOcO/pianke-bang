package com.campus.service;

import com.campus.dto.CouponIssueRequest;
import com.campus.dto.CouponPreviewRequest;
import com.campus.dto.CouponRedeemCodeCreateRequest;
import com.campus.dto.CouponRedeemRequest;
import com.campus.dto.CouponTemplateCreateRequest;
import com.campus.model.CouponClaimMode;
import com.campus.model.CouponRedeemCode;
import com.campus.model.CouponTemplate;
import com.campus.model.CouponTemplateStatus;
import com.campus.model.CouponType;
import com.campus.model.HallActivity;
import com.campus.repository.CouponTemplateRepository;
import com.campus.repository.HallActivityRepository;
import com.campus.security.AuthContext;
import com.campus.support.CouponCore;
import com.campus.support.CouponQuote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CouponService {

    private final CouponCore couponCore;
    private final CouponTemplateRepository couponTemplateRepository;
    private final HallActivityRepository hallActivityRepository;

    public CouponService(
            CouponCore couponCore,
            CouponTemplateRepository couponTemplateRepository,
            HallActivityRepository hallActivityRepository
    ) {
        this.couponCore = couponCore;
        this.couponTemplateRepository = couponTemplateRepository;
        this.hallActivityRepository = hallActivityRepository;
    }

    public List<Map<String, Object>> activities() {
        String user = AuthContext.getCurrentUser();
        List<Map<String, Object>> rows = couponCore.listActivities(
                user == null || user.isBlank() ? null : user.trim());
        // 已关联大厅活动且全部下架/隐藏的模板，不再出现在领券中心
        return rows.stream().filter(this::isClaimableInCouponCenter).toList();
    }

    public Map<String, Object> claimTemplate(Long templateId) {
        assertClaimableInCouponCenter(templateId);
        return couponCore.claimTemplate(requireUser(), templateId);
    }

    public List<Map<String, Object>> mine(String status) {
        String user = requireUser();
        return couponCore.listMine(user, status);
    }

    /** Agent / 内部只读：按用户名查券，不依赖 AuthContext */
    public List<Map<String, Object>> listForAgent(String username, String status) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return couponCore.listMine(username.trim(), status);
    }

    public Map<String, Object> preview(CouponPreviewRequest request) {
        String user = requireUser();
        if (request == null || request.getReward() == null) {
            throw new IllegalArgumentException("赏金不能为空");
        }
        CouponQuote quote = couponCore.preview(user, request.getUserCouponId(), request.getReward());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reward", quote.reward());
        out.put("discount", quote.discount());
        out.put("payable", quote.payable());
        out.put("reason", quote.reason());
        out.put("applicable", quote.applicable());
        return out;
    }

    public Map<String, Object> claimNewcomer() {
        return couponCore.claimNewcomer(requireUser());
    }

    public Map<String, Object> redeem(CouponRedeemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return couponCore.redeemByCode(requireUser(), request.getCode());
    }

    public CouponTemplate createTemplate(CouponTemplateCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        CouponType type = CouponType.valueOf(String.valueOf(request.getType()).trim().toUpperCase());
        if (type == CouponType.PERCENT) {
            BigDecimal percent = request.getDiscountPercent();
            if (percent == null) {
                throw new IllegalArgumentException("请选择折扣");
            }
            BigDecimal normalized = percent.setScale(1, java.math.RoundingMode.HALF_UP);
            int tenths = normalized.movePointRight(1).intValue();
            if (tenths < 1 || tenths > 9
                    || percent.subtract(normalized).abs().compareTo(new BigDecimal("0.001")) > 0) {
                throw new IllegalArgumentException("折扣须为 0.1～0.9，间隔 0.1");
            }
            request.setDiscountPercent(normalized);
        } else {
            BigDecimal amount = request.getDiscountAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("减免金额必须大于 0");
            }
        }
        CouponTemplate t = new CouponTemplate();
        t.setName(request.getName());
        t.setType(type);
        t.setDiscountAmount(request.getDiscountAmount());
        t.setMinSpend(request.getMinSpend() == null ? BigDecimal.ZERO : request.getMinSpend());
        t.setDiscountPercent(request.getDiscountPercent());
        t.setMaxDiscount(request.getMaxDiscount());
        t.setValidFrom(request.getValidFrom());
        t.setValidTo(request.getValidTo());
        t.setTotalStock(request.getTotalStock() == null ? 0 : Math.max(0, request.getTotalStock()));
        t.setPerUserLimit(request.getPerUserLimit() == null ? 1 : Math.max(1, request.getPerUserLimit()));
        t.setStatus(CouponTemplateStatus.ACTIVE);
        t.setDescription(request.getDescription());
        t.setSceneCode(request.getSceneCode());
        t.setBannerImageUrl(request.getBannerImageUrl());
        t.setBannerTheme(request.getBannerTheme());
        if (request.getClaimMode() != null && !request.getClaimMode().isBlank()) {
            t.setClaimMode(CouponClaimMode.valueOf(request.getClaimMode().trim().toUpperCase()));
        } else {
            t.setClaimMode(CouponClaimMode.ADMIN_ONLY);
        }
        return couponCore.createTemplate(t);
    }

    public Map<String, Object> issue(CouponIssueRequest request) {
        if (request == null || request.getTemplateId() == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        Map<String, Object> issued = couponCore.issueToUser(
                request.getTemplateId(), request.getUsername(), request.getCount());
        Map<String, Object> out = new LinkedHashMap<>(issued);
        if (request.getReason() != null && !request.getReason().isBlank()) {
            out.put("reason", request.getReason().trim());
        }
        out.put("message", "定向发券成功");
        return out;
    }

    public Map<String, Object> createRedeemCodes(CouponRedeemCodeCreateRequest request) {
        if (request == null || request.getTemplateId() == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        return couponCore.createRedeemCodes(
                request.getTemplateId(),
                request.getCode(),
                request.getCount(),
                request.getMaxUses(),
                request.getExpireAt(),
                request.getRemark()
        );
    }

    public List<CouponRedeemCode> listRedeemCodes(Long templateId) {
        return couponCore.listRedeemCodes(templateId);
    }

    public List<CouponTemplate> listTemplates() {
        return couponTemplateRepository.findAll();
    }

    public CouponTemplate updateTemplateStatus(Long templateId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("状态不能为空");
        }
        CouponTemplateStatus st;
        try {
            st = CouponTemplateStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("状态只支持 ACTIVE 或 OFF");
        }
        return couponCore.updateTemplateStatus(templateId, st);
    }

    public CouponTemplate updateTemplateBanner(Long templateId, Map<String, String> body) {
        String image = body == null ? null : body.get("bannerImageUrl");
        String theme = body == null ? null : body.get("bannerTheme");
        return couponCore.updateTemplateBanner(templateId, image, theme);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        // 解除大厅活动关联，避免脏引用
        List<HallActivity> linked = hallActivityRepository.findAll().stream()
                .filter(a -> templateId != null && templateId.equals(a.getCouponTemplateId()))
                .toList();
        for (HallActivity a : linked) {
            a.setCouponTemplateId(null);
        }
        if (!linked.isEmpty()) {
            hallActivityRepository.saveAll(linked);
        }
        couponCore.deleteTemplate(templateId);
    }

    public void ensureSeed() {
        couponCore.ensureNewcomerTemplates();
    }

    private boolean isClaimableInCouponCenter(Map<String, Object> card) {
        if (card == null) {
            return false;
        }
        Object rawId = card.get("templateId");
        if (!(rawId instanceof Number number)) {
            return true;
        }
        return isTemplateLinkedToActiveHallActivityOrStandalone(number.longValue());
    }

    private void assertClaimableInCouponCenter(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        if (!isTemplateLinkedToActiveHallActivityOrStandalone(templateId)) {
            throw new IllegalStateException("该活动已下架，暂不可领取");
        }
    }

    /**
     * 领券中心展示规则：
     * 1) 已绑定大厅活动：至少一条大厅活动为 ACTIVE；
     * 2) 未绑定的新人券/公开券：需要存在「领券中心」入口且为 ACTIVE（隐藏入口即关闭自领）。
     */
    private boolean isTemplateLinkedToActiveHallActivityOrStandalone(long templateId) {
        List<HallActivity> linked = hallActivityRepository.findByCouponTemplateId(templateId);
        if (!linked.isEmpty()) {
            return linked.stream().anyMatch(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()));
        }
        return hasActiveCouponCenterEntry();
    }

    private boolean hasActiveCouponCenterEntry() {
        return hallActivityRepository.findByStatusOrderBySortOrderAscIdDesc("ACTIVE").stream()
                .anyMatch(a -> HallActivityService.ACTION_COUPON_CENTER.equalsIgnoreCase(
                        a.getActionType() == null ? "" : a.getActionType()));
    }

    private String requireUser() {
        String user = AuthContext.getCurrentUser();
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        return user.trim();
    }
}
