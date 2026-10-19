package com.campus.rules;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 信誉分 / 超时处罚等跨服务共用规则。接单、客服话术、管理台解禁应引用此处，避免魔法数字漂移。
 */
public final class CreditRules {

    /** 接单最低信誉分 */
    public static final int MIN_TO_CLAIM = 60;

    /** 信誉分上限 */
    public static final int MAX = 100;

    /** 未落库时的默认分 */
    public static final int DEFAULT_SCORE = 80;

    /** 任务超时对接单员扣除的信誉分 */
    public static final int TIMEOUT_PENALTY = 10;

    /** 超时且分数低于接单线时的禁接时长（小时） */
    public static final int TIMEOUT_BLOCK_HOURS = 24;

    /** 超时补偿：按任务赏金比例赔给发布者 */
    public static final BigDecimal TIMEOUT_COMPENSATION_RATE = new BigDecimal("0.20");

    /** 每日自动恢复分数（封顶 {@link #MAX}） */
    public static final int RECOVERY_DAILY_POINTS = 5;

    private CreditRules() {
    }

    public static int effectiveScore(Integer creditScore) {
        return creditScore == null ? DEFAULT_SCORE : creditScore;
    }

    /** 禁接期是否仍有效 */
    public static boolean isBlocked(LocalDateTime blockedUntil, LocalDateTime now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    /**
     * 是否允许接单：分数达标且不在禁接期。
     * （已认证接单员等其它条件由调用方另行校验。）
     */
    public static boolean canClaimByCredit(Integer creditScore, LocalDateTime blockedUntil, LocalDateTime now) {
        return effectiveScore(creditScore) >= MIN_TO_CLAIM && !isBlocked(blockedUntil, now);
    }
}
