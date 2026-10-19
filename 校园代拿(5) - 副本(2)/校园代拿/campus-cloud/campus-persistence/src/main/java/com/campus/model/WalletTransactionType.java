package com.campus.model;

public enum WalletTransactionType {
    TASK_INCOME,
    /** 发布任务时从发布者钱包扣除的赏金（冻结） */
    TASK_BOUNTY_LOCK,
    /** 驳回/取消时退还发布者 */
    TASK_BOUNTY_REFUND,
    /** 超时补偿：从接单员扣除 */
    TIMEOUT_COMPENSATION_DEBIT,
    /** 超时补偿：给发布者入账 */
    TIMEOUT_COMPENSATION_CREDIT,
    /** 超时补偿欠款登记（余额不足） */
    TIMEOUT_COMPENSATION_DEBT,
    /** 后续收入自动抵扣欠款 */
    TIMEOUT_COMPENSATION_DEBT_REPAY,
    MANUAL_BONUS,
    /** 管理员后台给用户钱包充值 */
    ADMIN_CREDIT,
    /** 支付宝沙箱充值 */
    ALIPAY_RECHARGE,
    /** 支付宝沙箱提现（单笔转账） */
    ALIPAY_WITHDRAW,
    WITHDRAW_APPLY,
    WITHDRAW_APPROVE,
    WITHDRAW_REJECT,
    /** 平台补贴（记录优惠券导致的赏金差额，不改余额时可作审计流水） */
    COUPON_SUBSIDY
}
