package com.campus.support;

import com.campus.model.*;
import com.campus.repository.CouponRedeemCodeRepository;
import com.campus.repository.CouponRedeemLogRepository;
import com.campus.repository.CouponTemplateRepository;
import com.campus.repository.UserCouponRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同库优惠券核销核心（任务服务 / 钱包服务共用）。
 */
@Component
public class CouponCore {

    /** @deprecated 历史整包 scene，已拆为独立活动 */
    public static final String SCENE_NEWCOMER_PACK = "NEWCOMER_PACK";
    public static final String SCENE_NEWCOMER_Y5 = "NEWCOMER_Y5";
    public static final String SCENE_FIRST_PUBLISH_Y3 = "FIRST_PUBLISH_Y3";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRedeemCodeRepository couponRedeemCodeRepository;
    private final CouponRedeemLogRepository couponRedeemLogRepository;
    private final OssImageStore ossImageStore;

    public CouponCore(
            CouponTemplateRepository couponTemplateRepository,
            UserCouponRepository userCouponRepository,
            CouponRedeemCodeRepository couponRedeemCodeRepository,
            CouponRedeemLogRepository couponRedeemLogRepository,
            OssImageStore ossImageStore
    ) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponRedeemCodeRepository = couponRedeemCodeRepository;
        this.couponRedeemLogRepository = couponRedeemLogRepository;
        this.ossImageStore = ossImageStore;
    }

    public CouponQuote preview(String username, Long userCouponId, BigDecimal reward) {
        if (userCouponId == null) {
            return CouponQuote.none(reward);
        }
        UserCoupon coupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        assertOwned(coupon, username);
        CouponTemplate template = requireActiveTemplate(coupon);
        assertNotExpired(coupon, template);
        if (coupon.getStatus() != UserCouponStatus.UNUSED && coupon.getStatus() != UserCouponStatus.LOCKED) {
            throw new IllegalStateException("优惠券不可用");
        }
        CouponQuote quote = CouponCalculator.quote(template, reward);
        if (!quote.applicable()) {
            throw new IllegalStateException(quote.reason() == null || quote.reason().isBlank()
                    ? "优惠券不适用于该赏金"
                    : quote.reason());
        }
        return quote;
    }

    /** 钱包支付：直接核销为 USED */
    @Transactional
    public CouponQuote consume(String username, Long userCouponId, Long taskId, BigDecimal reward) {
        if (userCouponId == null) {
            return CouponQuote.none(reward);
        }
        UserCoupon coupon = userCouponRepository.findByIdForUpdate(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        assertOwned(coupon, username);
        if (coupon.getStatus() == UserCouponStatus.USED) {
            if (taskId != null && taskId.equals(coupon.getUsedTaskId())) {
                CouponTemplate template = requireTemplate(coupon.getTemplateId());
                return CouponCalculator.quote(template, reward);
            }
            throw new IllegalStateException("优惠券已使用");
        }
        if (coupon.getStatus() != UserCouponStatus.UNUSED && coupon.getStatus() != UserCouponStatus.LOCKED) {
            throw new IllegalStateException("优惠券状态不可用");
        }
        CouponTemplate template = requireActiveTemplate(coupon);
        assertNotExpired(coupon, template);
        CouponQuote quote = CouponCalculator.quote(template, reward);
        if (!quote.applicable()) {
            throw new IllegalStateException(quote.reason());
        }
        coupon.setStatus(UserCouponStatus.USED);
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setUsedTaskId(taskId);
        userCouponRepository.save(coupon);
        return quote;
    }

    /** 支付宝下单：占券 LOCKED */
    @Transactional
    public CouponQuote lock(String username, Long userCouponId, Long taskId, BigDecimal reward) {
        if (userCouponId == null) {
            return CouponQuote.none(reward);
        }
        UserCoupon coupon = userCouponRepository.findByIdForUpdate(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        assertOwned(coupon, username);
        if (coupon.getStatus() == UserCouponStatus.LOCKED && taskId != null && taskId.equals(coupon.getUsedTaskId())) {
            CouponTemplate template = requireTemplate(coupon.getTemplateId());
            return CouponCalculator.quote(template, reward);
        }
        if (coupon.getStatus() != UserCouponStatus.UNUSED) {
            throw new IllegalStateException("优惠券不可锁定");
        }
        CouponTemplate template = requireActiveTemplate(coupon);
        assertNotExpired(coupon, template);
        CouponQuote quote = CouponCalculator.quote(template, reward);
        if (!quote.applicable()) {
            throw new IllegalStateException(quote.reason());
        }
        coupon.setStatus(UserCouponStatus.LOCKED);
        coupon.setUsedTaskId(taskId);
        userCouponRepository.save(coupon);
        return quote;
    }

    /** 支付宝支付成功：LOCKED -> USED */
    @Transactional
    public void confirmLocked(Long userCouponId, Long taskId) {
        if (userCouponId == null) {
            return;
        }
        UserCoupon coupon = userCouponRepository.findByIdForUpdate(userCouponId).orElse(null);
        if (coupon == null) {
            return;
        }
        if (coupon.getStatus() == UserCouponStatus.USED) {
            return;
        }
        if (coupon.getStatus() == UserCouponStatus.LOCKED
                && (taskId == null || taskId.equals(coupon.getUsedTaskId()))) {
            coupon.setStatus(UserCouponStatus.USED);
            coupon.setUsedAt(LocalDateTime.now());
            coupon.setUsedTaskId(taskId);
            userCouponRepository.save(coupon);
        }
    }

    /** 取消/支付失败：退回 UNUSED */
    @Transactional
    public void restore(Long userCouponId, Long taskId) {
        if (userCouponId == null) {
            return;
        }
        UserCoupon coupon = userCouponRepository.findByIdForUpdate(userCouponId).orElse(null);
        if (coupon == null) {
            return;
        }
        if (taskId != null && coupon.getUsedTaskId() != null && !taskId.equals(coupon.getUsedTaskId())) {
            return;
        }
        if (coupon.getStatus() == UserCouponStatus.USED || coupon.getStatus() == UserCouponStatus.LOCKED) {
            coupon.setStatus(UserCouponStatus.UNUSED);
            coupon.setUsedAt(null);
            coupon.setUsedTaskId(null);
            userCouponRepository.save(coupon);
        }
    }

    public List<Map<String, Object>> listMine(String username, String statusFilter) {
        List<UserCoupon> list;
        if (statusFilter != null && !statusFilter.isBlank()) {
            UserCouponStatus st = UserCouponStatus.valueOf(statusFilter.trim().toUpperCase());
            list = userCouponRepository.findByUsernameIgnoreCaseAndStatusOrderByObtainedAtDesc(username, st);
        } else {
            list = userCouponRepository.findByUsernameIgnoreCaseOrderByObtainedAtDesc(username);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserCoupon c : list) {
            CouponTemplate t = couponTemplateRepository.findById(c.getTemplateId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("templateId", c.getTemplateId());
            row.put("status", c.getStatus().name());
            row.put("obtainedAt", c.getObtainedAt());
            row.put("expireAt", c.getExpireAt());
            row.put("usedAt", c.getUsedAt());
            row.put("usedTaskId", c.getUsedTaskId());
            if (t != null) {
                row.put("name", t.getName());
                row.put("type", t.getType().name());
                row.put("discountAmount", t.getDiscountAmount());
                row.put("minSpend", t.getMinSpend());
                row.put("discountPercent", t.getDiscountPercent());
                row.put("maxDiscount", t.getMaxDiscount());
                row.put("sceneCode", t.getSceneCode());
                String resolvedTheme = t.getBannerTheme() == null || t.getBannerTheme().isBlank()
                        ? themeForScene(t.getSceneCode())
                        : t.getBannerTheme().trim().toLowerCase();
                row.put("bannerTheme", resolvedTheme);
                if (t.getBannerImageUrl() != null && !t.getBannerImageUrl().isBlank()) {
                    row.put("bannerImageUrl", t.getBannerImageUrl());
                }
            }
            out.add(row);
        }
        return out;
    }

    @Transactional
    public CouponTemplate createTemplate(CouponTemplate template) {
        if (template.getName() == null || template.getName().isBlank()) {
            throw new IllegalArgumentException("优惠券名称不能为空");
        }
        if (template.getType() == null) {
            throw new IllegalArgumentException("优惠券类型不能为空");
        }
        if (template.getStatus() == null) {
            template.setStatus(CouponTemplateStatus.ACTIVE);
        }
        if (template.getClaimMode() == null) {
            template.setClaimMode(CouponClaimMode.ADMIN_ONLY);
        }
        if (template.getMinSpend() == null) {
            template.setMinSpend(BigDecimal.ZERO);
        }
        if (template.getTotalStock() == null) {
            template.setTotalStock(0);
        }
        if (template.getIssuedCount() == null) {
            template.setIssuedCount(0);
        }
        if (template.getPerUserLimit() == null || template.getPerUserLimit() < 1) {
            template.setPerUserLimit(1);
        }
        if (template.getBannerImageUrl() != null && !template.getBannerImageUrl().isBlank()) {
            template.setBannerImageUrl(ossImageStore.storeDataUrl(template.getBannerImageUrl(), "banner"));
        }
        return couponTemplateRepository.save(template);
    }

    @Transactional
    public Map<String, Object> issueToUser(Long templateId, String username, Integer count) {
        // 用户主动领取路径外也会走这里；是否上线由调用方决定
        return issueToUserInternal(templateId, username, count, false);
    }

    /** 用户主动领：必须活动上线 */
    @Transactional
    public Map<String, Object> issueToUserIfActive(Long templateId, String username, Integer count) {
        return issueToUserInternal(templateId, username, count, true);
    }

    private Map<String, Object> issueToUserInternal(
            Long templateId,
            String username,
            Integer count,
            boolean requireActive
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        int n = count == null || count < 1 ? 1 : Math.min(count, 20);
        CouponTemplate template = requireActive
                ? requireActiveTemplateById(templateId)
                : requireTemplate(templateId);
        long owned = userCouponRepository.countByTemplateIdAndUsernameIgnoreCase(templateId, username.trim());
        int limit = template.getPerUserLimit() == null ? 1 : template.getPerUserLimit();
        if (owned + n > limit) {
            throw new IllegalStateException("超过每人限领数量（已有 " + owned + "，上限 " + limit + "）");
        }
        int stock = template.getTotalStock() == null ? 0 : template.getTotalStock();
        int issued = template.getIssuedCount() == null ? 0 : template.getIssuedCount();
        if (stock > 0 && issued + n > stock) {
            throw new IllegalStateException("券库存不足");
        }
        List<Long> ids = new ArrayList<>();
        LocalDateTime expireAt = template.getValidTo();
        for (int i = 0; i < n; i++) {
            UserCoupon c = new UserCoupon();
            c.setTemplateId(templateId);
            c.setUsername(username.trim());
            c.setStatus(UserCouponStatus.UNUSED);
            c.setExpireAt(expireAt);
            userCouponRepository.save(c);
            ids.add(c.getId());
        }
        template.setIssuedCount(issued + n);
        couponTemplateRepository.save(template);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("issued", n);
        out.put("couponIds", ids);
        return out;
    }

    /** 活动列表（公开）：每个可领模板独立成活动卡片（需登录后点击领取，非注册自动发） */
    public List<Map<String, Object>> listActivities(String username) {
        ensureNewcomerTemplates();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CouponTemplate t : couponTemplateRepository
                .findByClaimModeAndStatusOrderByIdAsc(CouponClaimMode.NEWCOMER, CouponTemplateStatus.ACTIVE)) {
            out.add(toActivityCard(t, username, themeForScene(t.getSceneCode())));
        }
        for (CouponTemplate t : couponTemplateRepository
                .findByClaimModeAndStatusOrderByIdAsc(CouponClaimMode.PUBLIC, CouponTemplateStatus.ACTIVE)) {
            out.add(toActivityCard(t, username, "green"));
        }
        return out;
    }

    private Map<String, Object> toActivityCard(CouponTemplate t, String username, String theme) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "template-" + t.getId());
        card.put("action", "CLAIM_TEMPLATE");
        card.put("templateId", t.getId());
        card.put("sceneCode", t.getSceneCode());
        card.put("title", t.getName());
        card.put("subtitle", t.getDescription() == null || t.getDescription().isBlank()
                ? formatTemplateBrief(t) : t.getDescription());
        String resolvedTheme = t.getBannerTheme() == null || t.getBannerTheme().isBlank()
                ? theme : t.getBannerTheme().trim().toLowerCase();
        card.put("theme", resolvedTheme);
        card.put("bannerTheme", resolvedTheme);
        if (t.getBannerImageUrl() != null && !t.getBannerImageUrl().isBlank()) {
            card.put("bannerImageUrl", t.getBannerImageUrl());
        }
        card.put("amountLabel", amountLabel(t));
        boolean owned = username != null && !username.isBlank()
                && userCouponRepository.countByTemplateIdAndUsernameIgnoreCase(t.getId(), username) > 0;
        card.put("claimed", owned);
        card.put("claimable", !owned);
        return card;
    }

    private String amountLabel(CouponTemplate t) {
        if (t.getType() == CouponType.PERCENT && t.getDiscountPercent() != null) {
            return t.getDiscountPercent().multiply(new BigDecimal("10")).stripTrailingZeros().toPlainString() + "折";
        }
        BigDecimal amt = t.getDiscountAmount() == null ? BigDecimal.ZERO : t.getDiscountAmount();
        return "¥" + amt.stripTrailingZeros().toPlainString();
    }

    private String themeForScene(String scene) {
        if (SCENE_FIRST_PUBLISH_Y3.equals(scene)) {
            return "cool";
        }
        if (SCENE_NEWCOMER_Y5.equals(scene) || SCENE_NEWCOMER_PACK.equals(scene)) {
            return "warm";
        }
        return "green";
    }

    /** 兼容旧客户端：仅领取 ¥5 新人活动券 */
    @Transactional
    public Map<String, Object> claimNewcomer(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        ensureNewcomerTemplates();
        List<CouponTemplate> pack = couponTemplateRepository
                .findBySceneCodeAndStatusOrderByIdAsc(SCENE_NEWCOMER_Y5, CouponTemplateStatus.ACTIVE);
        if (pack.isEmpty()) {
            pack = couponTemplateRepository
                    .findBySceneCodeAndStatusOrderByIdAsc(SCENE_NEWCOMER_PACK, CouponTemplateStatus.ACTIVE);
        }
        if (pack.isEmpty()) {
            throw new IllegalStateException("新人活动暂未开放");
        }
        CouponTemplate t = pack.get(0);
        long owned = userCouponRepository.countByTemplateIdAndUsernameIgnoreCase(t.getId(), username.trim());
        if (owned > 0) {
            throw new IllegalStateException("你已领取过该活动券");
        }
        Map<String, Object> issued = issueToUserIfActive(t.getId(), username, 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "领取成功");
        out.put("claimed", 1);
        out.put("couponIds", issued.get("couponIds"));
        out.put("templateId", t.getId());
        return out;
    }

    @Transactional
    public Map<String, Object> claimTemplate(String username, Long templateId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        CouponTemplate template = requireActiveTemplateById(templateId);
        if (template.getClaimMode() != CouponClaimMode.PUBLIC
                && template.getClaimMode() != CouponClaimMode.NEWCOMER) {
            throw new IllegalStateException("该券不可主动领取，请等待活动发放");
        }
        Map<String, Object> issued = issueToUserIfActive(templateId, username, 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "领取成功");
        out.put("issued", issued.get("issued"));
        out.put("couponIds", issued.get("couponIds"));
        return out;
    }

    @Transactional
    public Map<String, Object> redeemByCode(String username, String rawCode) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("兑换码不能为空");
        }
        String code = rawCode.trim().toUpperCase();
        CouponRedeemCode redeem = couponRedeemCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("兑换码无效"));
        if (redeem.getStatus() != CouponRedeemCodeStatus.ACTIVE) {
            throw new IllegalStateException("兑换码已失效");
        }
        if (redeem.getExpireAt() != null && LocalDateTime.now().isAfter(redeem.getExpireAt())) {
            redeem.setStatus(CouponRedeemCodeStatus.DISABLED);
            couponRedeemCodeRepository.save(redeem);
            throw new IllegalStateException("兑换码已过期");
        }
        if (couponRedeemLogRepository.existsByCodeIdAndUsernameIgnoreCase(redeem.getId(), username.trim())) {
            throw new IllegalStateException("你已使用过该兑换码");
        }
        int max = redeem.getMaxUses() == null ? 1 : redeem.getMaxUses();
        int used = redeem.getUsedCount() == null ? 0 : redeem.getUsedCount();
        if (used >= max) {
            redeem.setStatus(CouponRedeemCodeStatus.EXHAUSTED);
            couponRedeemCodeRepository.save(redeem);
            throw new IllegalStateException("兑换码已用完");
        }

        // 兑换码可在模板下线后仍兑换；核销时再校验券有效期
        Map<String, Object> issued = issueToUser(redeem.getTemplateId(), username, 1);
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) issued.get("couponIds");
        Long userCouponId = ids == null || ids.isEmpty() ? null : ids.get(0);

        redeem.setUsedCount(used + 1);
        if (redeem.getUsedCount() >= max) {
            redeem.setStatus(CouponRedeemCodeStatus.EXHAUSTED);
        }
        couponRedeemCodeRepository.save(redeem);

        CouponRedeemLog log = new CouponRedeemLog();
        log.setCodeId(redeem.getId());
        log.setUsername(username.trim());
        log.setUserCouponId(userCouponId);
        couponRedeemLogRepository.save(log);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "兑换成功");
        out.put("couponIds", ids);
        out.put("templateId", redeem.getTemplateId());
        return out;
    }

    @Transactional
    public Map<String, Object> createRedeemCodes(
            Long templateId,
            String fixedCode,
            Integer count,
            Integer maxUses,
            LocalDateTime expireAt,
            String remark
    ) {
        requireTemplate(templateId);
        int max = maxUses == null || maxUses < 1 ? 1 : Math.min(maxUses, 10000);
        List<String> codes = new ArrayList<>();
        if (fixedCode != null && !fixedCode.isBlank()) {
            String code = fixedCode.trim().toUpperCase();
            if (couponRedeemCodeRepository.existsByCodeIgnoreCase(code)) {
                throw new IllegalStateException("兑换码已存在：" + code);
            }
            CouponRedeemCode row = new CouponRedeemCode();
            row.setCode(code);
            row.setTemplateId(templateId);
            row.setMaxUses(max);
            row.setUsedCount(0);
            row.setStatus(CouponRedeemCodeStatus.ACTIVE);
            row.setExpireAt(expireAt);
            row.setRemark(remark);
            couponRedeemCodeRepository.save(row);
            codes.add(code);
        } else {
            int n = count == null || count < 1 ? 1 : Math.min(count, 50);
            for (int i = 0; i < n; i++) {
                String code = generateUniqueCode();
                CouponRedeemCode row = new CouponRedeemCode();
                row.setCode(code);
                row.setTemplateId(templateId);
                row.setMaxUses(max);
                row.setUsedCount(0);
                row.setStatus(CouponRedeemCodeStatus.ACTIVE);
                row.setExpireAt(expireAt);
                row.setRemark(remark);
                couponRedeemCodeRepository.save(row);
                codes.add(code);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", codes.size());
        out.put("codes", codes);
        out.put("templateId", templateId);
        return out;
    }

    public List<CouponRedeemCode> listRedeemCodes(Long templateId) {
        if (templateId == null) {
            return couponRedeemCodeRepository.findAllByOrderByCreatedAtDesc();
        }
        return couponRedeemCodeRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
    }

    /** 启动时灌入两个独立活动券（若不存在），并迁移旧整包 scene */
    @Transactional
    public void ensureNewcomerTemplates() {
        for (CouponTemplate existing : couponTemplateRepository.findAll()) {
            String name = existing.getName() == null ? "" : existing.getName().trim();
            boolean dirty = false;
            if ("新人赏金立减¥5".equals(name)
                    && (existing.getSceneCode() == null
                    || existing.getSceneCode().isBlank()
                    || SCENE_NEWCOMER_PACK.equals(existing.getSceneCode()))) {
                existing.setSceneCode(SCENE_NEWCOMER_Y5);
                existing.setDescription("登录后领取，发布任务支付赏金时可抵扣 ¥5");
                dirty = true;
            }
            if ("首单发布立减¥3".equals(name)
                    && (existing.getSceneCode() == null
                    || existing.getSceneCode().isBlank()
                    || SCENE_NEWCOMER_PACK.equals(existing.getSceneCode()))) {
                existing.setSceneCode(SCENE_FIRST_PUBLISH_Y3);
                existing.setDescription("登录后领取，首单发布支付赏金时可抵扣 ¥3");
                dirty = true;
            }
            if ("新人赏金立减¥5".equals(name) && (existing.getBannerTheme() == null || existing.getBannerTheme().isBlank())) {
                existing.setBannerTheme("warm");
                dirty = true;
            }
            if ("首单发布立减¥3".equals(name) && (existing.getBannerTheme() == null || existing.getBannerTheme().isBlank())) {
                existing.setBannerTheme("cool");
                dirty = true;
            }
            if (dirty) {
                couponTemplateRepository.save(existing);
            }
        }
        if (!couponTemplateRepository.existsByNameAndClaimMode("新人赏金立减¥5", CouponClaimMode.NEWCOMER)) {
            CouponTemplate t = new CouponTemplate();
            t.setName("新人赏金立减¥5");
            t.setType(CouponType.FIXED);
            t.setDiscountAmount(new BigDecimal("5.00"));
            t.setMinSpend(BigDecimal.ZERO);
            t.setClaimMode(CouponClaimMode.NEWCOMER);
            t.setSceneCode(SCENE_NEWCOMER_Y5);
            t.setDescription("登录后领取，发布任务支付赏金时可抵扣 ¥5");
            t.setBannerTheme("warm");
            t.setTotalStock(0);
            t.setPerUserLimit(1);
            t.setStatus(CouponTemplateStatus.ACTIVE);
            t.setValidTo(LocalDateTime.now().plusYears(1));
            createTemplate(t);
        }
        if (!couponTemplateRepository.existsByNameAndClaimMode("首单发布立减¥3", CouponClaimMode.NEWCOMER)) {
            CouponTemplate t = new CouponTemplate();
            t.setName("首单发布立减¥3");
            t.setType(CouponType.FIXED);
            t.setDiscountAmount(new BigDecimal("3.00"));
            t.setMinSpend(BigDecimal.ZERO);
            t.setClaimMode(CouponClaimMode.NEWCOMER);
            t.setSceneCode(SCENE_FIRST_PUBLISH_Y3);
            t.setDescription("登录后领取，首单发布支付赏金时可抵扣 ¥3");
            t.setBannerTheme("cool");
            t.setTotalStock(0);
            t.setPerUserLimit(1);
            t.setStatus(CouponTemplateStatus.ACTIVE);
            t.setValidTo(LocalDateTime.now().plusYears(1));
            createTemplate(t);
        }
    }

    @Transactional
    public CouponTemplate updateTemplateStatus(Long templateId, CouponTemplateStatus status) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("状态不能为空");
        }
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        template.setStatus(status);
        return couponTemplateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        long owned = userCouponRepository.countByTemplateId(templateId);
        if (owned > 0) {
            throw new IllegalStateException(
                    "该模板已有 " + owned + " 张用户券，无法删除。请改用「停用领取」。");
        }
        // 先删兑换日志 → 兑换码 → 模板，避免外键挡住删除
        List<CouponRedeemCode> codes = couponRedeemCodeRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        if (!codes.isEmpty()) {
            List<Long> codeIds = codes.stream().map(CouponRedeemCode::getId).filter(id -> id != null).toList();
            if (!codeIds.isEmpty()) {
                couponRedeemLogRepository.deleteByCodeIdIn(codeIds);
            }
            couponRedeemCodeRepository.deleteAll(codes);
        }
        couponTemplateRepository.delete(template);
    }

    private String formatTemplateBrief(CouponTemplate t) {
        if (t.getType() == CouponType.FIXED) {
            return "立减 ¥" + (t.getDiscountAmount() == null ? "0" : t.getDiscountAmount().toPlainString());
        }
        if (t.getType() == CouponType.THRESHOLD) {
            return "满" + nullToZero(t.getMinSpend()).toPlainString()
                    + "减" + nullToZero(t.getDiscountAmount()).toPlainString();
        }
        if (t.getType() == CouponType.PERCENT && t.getDiscountPercent() != null) {
            return "享" + t.getDiscountPercent().multiply(new BigDecimal("10")).stripTrailingZeros().toPlainString() + "折";
        }
        return "赏金优惠券";
    }

    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (!couponRedeemCodeRepository.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("生成兑换码失败，请重试");
    }

    private void assertOwned(UserCoupon coupon, String username) {
        if (username == null || !username.equalsIgnoreCase(coupon.getUsername())) {
            throw new IllegalStateException("无权使用该优惠券");
        }
    }

    /** 已持有券核销：允许活动已下线，仍校验有效期 */
    private CouponTemplate requireActiveTemplate(UserCoupon coupon) {
        CouponTemplate template = requireTemplate(coupon.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        if (template.getValidFrom() != null && now.isBefore(template.getValidFrom())) {
            throw new IllegalStateException("优惠券尚未开始");
        }
        if (template.getValidTo() != null && now.isAfter(template.getValidTo())) {
            throw new IllegalStateException("优惠券活动已结束");
        }
        return template;
    }

    private CouponTemplate requireActiveTemplateById(Long templateId) {
        CouponTemplate template = requireTemplate(templateId);
        if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
            throw new IllegalStateException("优惠券活动已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getValidFrom() != null && now.isBefore(template.getValidFrom())) {
            throw new IllegalStateException("优惠券尚未开始");
        }
        if (template.getValidTo() != null && now.isAfter(template.getValidTo())) {
            throw new IllegalStateException("优惠券活动已结束");
        }
        return template;
    }

    @Transactional
    public CouponTemplate updateTemplateBanner(Long templateId, String bannerImageUrl, String bannerTheme) {
        CouponTemplate template = requireTemplate(templateId);
        if (bannerImageUrl != null) {
            String url = bannerImageUrl.trim();
            template.setBannerImageUrl(url.isEmpty() ? null : ossImageStore.storeDataUrl(url, "banner"));
        }
        if (bannerTheme != null) {
            String theme = bannerTheme.trim().toLowerCase();
            template.setBannerTheme(theme.isEmpty() ? null : theme);
        }
        return couponTemplateRepository.save(template);
    }

    private CouponTemplate requireTemplate(Long templateId) {
        return couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
    }

    private void assertNotExpired(UserCoupon coupon, CouponTemplate template) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = coupon.getExpireAt() != null ? coupon.getExpireAt() : template.getValidTo();
        if (expire != null && now.isAfter(expire)) {
            if (coupon.getStatus() == UserCouponStatus.UNUSED) {
                coupon.setStatus(UserCouponStatus.EXPIRED);
                userCouponRepository.save(coupon);
            }
            throw new IllegalStateException("优惠券已过期");
        }
    }
}
