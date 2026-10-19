package com.campus.support;

import java.math.BigDecimal;

/**
 * 任务侧钱包操作抽象：同库部署用 {@link LocalWalletSupport}，跨库/独立库用 {@link RemoteWalletSupport}。
 */
public interface WalletSupport {

    void lockTaskBounty(String publisherName, BigDecimal amount, Long taskId, String title);

    void refundTaskBounty(String publisherName, Long taskId, BigDecimal amount, String remark);

    void incomeFromTask(String claimerName, BigDecimal amount, Long taskId, String title);

    void recordCouponSubsidy(String publisherName, Long taskId, BigDecimal discount, String title);

    void compensatePublisherForTimeout(String riderName, String publisherName, BigDecimal amount, Long taskId, String title);

    void creditManualForDemo(String username, BigDecimal amount);
}
