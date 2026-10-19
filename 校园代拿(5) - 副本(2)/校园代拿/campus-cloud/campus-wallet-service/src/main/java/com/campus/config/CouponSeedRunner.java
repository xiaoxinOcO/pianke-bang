package com.campus.config;

import com.campus.service.CouponService;
import com.campus.service.HallActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CouponSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CouponSeedRunner.class);

    private final CouponService couponService;
    private final HallActivityService hallActivityService;

    public CouponSeedRunner(CouponService couponService, HallActivityService hallActivityService) {
        this.couponService = couponService;
        this.hallActivityService = hallActivityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            couponService.ensureSeed();
            log.info("优惠券种子数据已就绪（新人礼包）");
        } catch (Exception ex) {
            log.warn("优惠券种子初始化失败：{}", ex.getMessage());
        }
        try {
            hallActivityService.ensureSeed();
            log.info("大厅活动种子数据已就绪");
        } catch (Exception ex) {
            log.warn("大厅活动种子初始化失败：{}", ex.getMessage());
        }
    }
}
