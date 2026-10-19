package com.campus.model;

/** 券发放/领取方式 */
public enum CouponClaimMode {
    /** 仅管理员定向发放 */
    ADMIN_ONLY,
    /** 活动页用户可主动领取 */
    PUBLIC,
    /** 新人礼包领取 */
    NEWCOMER
}
