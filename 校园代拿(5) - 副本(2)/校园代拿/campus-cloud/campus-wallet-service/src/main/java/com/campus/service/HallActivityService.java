package com.campus.service;

import com.campus.dto.HallActivityUpsertRequest;
import com.campus.model.CouponClaimMode;
import com.campus.model.CouponTemplate;
import com.campus.model.CouponTemplateStatus;
import com.campus.model.CouponType;
import com.campus.model.HallActivity;
import com.campus.repository.HallActivityRepository;
import com.campus.repository.UserCouponRepository;
import com.campus.security.AuthContext;
import com.campus.support.CouponCore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HallActivityService {

    public static final String ACTION_RIDER_APPLY = "RIDER_APPLY";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_COUPON_CLAIM = "COUPON_CLAIM";
    public static final String ACTION_COUPON_CENTER = "COUPON_CENTER";
    public static final String ACTION_WALLET = "WALLET";
    public static final String ACTION_CUSTOM_PATH = "CUSTOM_PATH";

    private static final Set<String> ACTIONS = Set.of(
            ACTION_RIDER_APPLY,
            ACTION_PUBLISH,
            ACTION_COUPON_CLAIM,
            ACTION_COUPON_CENTER,
            ACTION_WALLET,
            ACTION_CUSTOM_PATH
    );

    private final HallActivityRepository hallActivityRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponCore couponCore;

    public HallActivityService(
            HallActivityRepository hallActivityRepository,
            UserCouponRepository userCouponRepository,
            CouponCore couponCore
    ) {
        this.hallActivityRepository = hallActivityRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponCore = couponCore;
    }

    public List<Map<String, Object>> listPublic() {
        String user = AuthContext.getCurrentUser();
        String username = user == null || user.isBlank() ? null : user.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (HallActivity a : hallActivityRepository.findByStatusOrderBySortOrderAscIdDesc("ACTIVE")) {
            out.add(toPublicCard(a, username));
        }
        return out;
    }

    public List<HallActivity> listAll() {
        return hallActivityRepository.findAllByOrderBySortOrderAscIdDesc();
    }

    @Transactional
    public HallActivity create(HallActivityUpsertRequest request) {
        HallActivity a = new HallActivity();
        apply(a, request, true);
        return hallActivityRepository.save(a);
    }

    @Transactional
    public HallActivity update(Long id, HallActivityUpsertRequest request) {
        HallActivity a = hallActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        apply(a, request, false);
        return hallActivityRepository.save(a);
    }

    @Transactional
    public HallActivity updateStatus(Long id, String status) {
        HallActivity a = hallActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(st) && !"OFF".equals(st)) {
            throw new IllegalArgumentException("状态只支持 ACTIVE 或 OFF");
        }
        a.setStatus(st);
        HallActivity saved = hallActivityRepository.save(a);
        syncLinkedCouponTemplateStatus(saved.getCouponTemplateId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        HallActivity a = hallActivityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        Long templateId = a.getCouponTemplateId();
        hallActivityRepository.deleteById(id);
        syncLinkedCouponTemplateStatus(templateId);
    }

    /**
     * 大厅领券活动隐藏/删除后，同步关联优惠券模板：
     * 若没有其他「展示中」的大厅活动引用该模板，则停用领取；重新展示时再启用。
     */
    private void syncLinkedCouponTemplateStatus(Long templateId) {
        if (templateId == null) {
            return;
        }
        List<HallActivity> linked = hallActivityRepository.findByCouponTemplateId(templateId);
        boolean anyActive = linked.stream()
                .anyMatch(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()));
        couponCore.updateTemplateStatus(
                templateId,
                anyActive ? CouponTemplateStatus.ACTIVE : CouponTemplateStatus.OFF
        );
    }

    /** 首次空库时灌入示例活动 */
    @Transactional
    public void ensureSeed() {
        if (hallActivityRepository.count() > 0) {
            return;
        }
        createSeed("点击认证，马上接单赚钱", "完成接单员认证后可接大厅任务", "立即认证",
                ACTION_RIDER_APPLY, null, "green", 10);
        createSeed("一键发布校园跑腿", "填写取送信息，同学顺路帮你搞定", "去发布",
                ACTION_PUBLISH, null, "cool", 20);
        createSeed("领券中心", "新人礼包与活动券都在这里", "去领券",
                ACTION_COUPON_CENTER, null, "warm", 30);
    }

    private void createSeed(
            String title,
            String subtitle,
            String cta,
            String action,
            Long couponTemplateId,
            String theme,
            int sort
    ) {
        if (hallActivityRepository.existsByTitleAndActionType(title, action)) {
            return;
        }
        HallActivity a = new HallActivity();
        a.setTitle(title);
        a.setSubtitle(subtitle);
        a.setCtaText(cta);
        a.setActionType(action);
        a.setCouponTemplateId(couponTemplateId);
        a.setBannerTheme(theme);
        a.setSortOrder(sort);
        a.setStatus("ACTIVE");
        hallActivityRepository.save(a);
    }

    private void apply(HallActivity a, HallActivityUpsertRequest request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("请填写活动标题");
        }
        if (title.length() > 12) {
            throw new IllegalArgumentException("活动标题最多 12 字");
        }
        String subtitleRaw = request.getSubtitle() == null ? "" : request.getSubtitle().trim();
        if (subtitleRaw.length() > 8) {
            throw new IllegalArgumentException("副文案最多 8 字");
        }
        String action = request.getActionType() == null ? "" : request.getActionType().trim().toUpperCase();
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("活动类型无效，可选：接单认证/发单/领券/领券中心/钱包/自定义页");
        }
        a.setTitle(title);
        a.setSubtitle(blankToNull(subtitleRaw));
        if (request.getBannerImageUrl() != null) {
            String img = request.getBannerImageUrl().trim();
            a.setBannerImageUrl(img.isEmpty() ? null : img);
        } else if (creating) {
            a.setBannerImageUrl(null);
        }
        if (request.getBannerImageUrlWeb() != null) {
            String imgWeb = request.getBannerImageUrlWeb().trim();
            a.setBannerImageUrlWeb(imgWeb.isEmpty() ? null : imgWeb);
        } else if (creating) {
            a.setBannerImageUrlWeb(null);
        }
        String theme = request.getBannerTheme() == null || request.getBannerTheme().isBlank()
                ? "warm" : request.getBannerTheme().trim().toLowerCase();
        a.setBannerTheme(theme);

        String cta = request.getCtaText() == null || request.getCtaText().isBlank()
                ? defaultCta(action) : request.getCtaText().trim();
        if (cta.length() > 5) {
            throw new IllegalArgumentException("按钮文案最多 5 字");
        }
        a.setCtaText(cta);
        a.setActionType(action);

        if (ACTION_CUSTOM_PATH.equals(action)) {
            String path = request.getActionValue() == null ? "" : request.getActionValue().trim();
            if (path.isEmpty() || !path.startsWith("/")) {
                throw new IllegalArgumentException("自定义页面路径需以 / 开头，如 /pages/rider-apply/rider-apply");
            }
            a.setActionValue(path);
        } else {
            a.setActionValue(blankToNull(request.getActionValue()));
        }

        if (ACTION_COUPON_CLAIM.equals(action)) {
            Long templateId = request.getCouponTemplateId();
            if (templateId == null && request.getCouponAmount() != null && request.getCouponAmount() > 0) {
                templateId = createLinkedCoupon(
                        title,
                        request.getCouponAmount(),
                        theme,
                        a.getSubtitle() == null ? "" : a.getSubtitle(),
                        request.getCouponValidDays()
                );
            }
            if (templateId == null) {
                throw new IllegalArgumentException("领券活动请选择优惠券模板，或填写优惠金额自动创建");
            }
            a.setCouponTemplateId(templateId);
        } else if (request.getCouponTemplateId() != null) {
            a.setCouponTemplateId(request.getCouponTemplateId());
        } else if (creating) {
            a.setCouponTemplateId(null);
        }

        a.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String st = request.getStatus().trim().toUpperCase();
            if (!"ACTIVE".equals(st) && !"OFF".equals(st)) {
                throw new IllegalArgumentException("状态只支持 ACTIVE 或 OFF");
            }
            a.setStatus(st);
        } else if (creating) {
            a.setStatus("ACTIVE");
        }
    }

    private Long createLinkedCoupon(String title, double amount, String theme, String subtitle, Integer validDays) {
        CouponTemplate t = new CouponTemplate();
        t.setName(title.length() > 80 ? title.substring(0, 80) : title);
        t.setType(CouponType.FIXED);
        t.setDiscountAmount(BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP));
        t.setMinSpend(BigDecimal.ZERO);
        t.setClaimMode(CouponClaimMode.PUBLIC);
        t.setDescription(subtitle == null || subtitle.isBlank()
                ? ("登录后领取，发布任务支付赏金时可抵扣 ¥" + t.getDiscountAmount().toPlainString())
                : subtitle);
        t.setBannerTheme(theme);
        t.setPerUserLimit(1);
        t.setTotalStock(0);
        t.setStatus(CouponTemplateStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        t.setValidFrom(now);
        int days = validDays == null ? 7 : validDays;
        if (days > 0) {
            if (days > 3650) {
                throw new IllegalArgumentException("优惠券有效天数不能超过 3650 天");
            }
            t.setValidTo(now.plusDays(days).withHour(23).withMinute(59).withSecond(59).withNano(0));
        } else {
            // 0 / 负数：不设截止，领取后长期可用
            t.setValidTo(null);
        }
        CouponTemplate saved = couponCore.createTemplate(t);
        return saved.getId();
    }

    private Map<String, Object> toPublicCard(HallActivity a, String username) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "hall-" + a.getId());
        card.put("activityId", a.getId());
        card.put("title", a.getTitle());
        card.put("subtitle", a.getSubtitle() == null ? "" : a.getSubtitle());
        card.put("bannerTheme", a.getBannerTheme() == null || a.getBannerTheme().isBlank() ? "warm" : a.getBannerTheme());
        card.put("theme", card.get("bannerTheme"));
        if (a.getBannerImageUrl() != null && !a.getBannerImageUrl().isBlank()) {
            card.put("bannerImageUrl", a.getBannerImageUrl());
        }
        if (a.getBannerImageUrlWeb() != null && !a.getBannerImageUrlWeb().isBlank()) {
            card.put("bannerImageUrlWeb", a.getBannerImageUrlWeb());
        }
        card.put("ctaText", a.getCtaText() == null || a.getCtaText().isBlank() ? defaultCta(a.getActionType()) : a.getCtaText());
        card.put("actionType", a.getActionType());
        card.put("actionValue", a.getActionValue());
        card.put("couponTemplateId", a.getCouponTemplateId());
        card.put("templateId", a.getCouponTemplateId());
        boolean claimed = false;
        if (ACTION_COUPON_CLAIM.equals(a.getActionType())
                && a.getCouponTemplateId() != null
                && username != null) {
            claimed = userCouponRepository.countByTemplateIdAndUsernameIgnoreCase(a.getCouponTemplateId(), username) > 0;
        }
        card.put("claimed", claimed);
        card.put("sortOrder", a.getSortOrder());
        return card;
    }

    private static String defaultCta(String action) {
        return switch (action) {
            case ACTION_RIDER_APPLY -> "立即认证";
            case ACTION_PUBLISH -> "去发布";
            case ACTION_COUPON_CLAIM -> "去领取";
            case ACTION_COUPON_CENTER -> "去领券";
            case ACTION_WALLET -> "看钱包";
            case ACTION_CUSTOM_PATH -> "去看看";
            default -> "去看看";
        };
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
