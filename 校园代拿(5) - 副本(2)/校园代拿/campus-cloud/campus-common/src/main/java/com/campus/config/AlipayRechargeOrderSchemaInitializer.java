package com.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 历史库中 alipay_recharge_order.status 可能是 ENUM，不含 REFUNDED，导致退款后更新失败。
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class AlipayRechargeOrderSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AlipayRechargeOrderSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AlipayRechargeOrderSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = DATABASE()
                              AND table_name = 'alipay_recharge_order'
                            """,
                    Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE alipay_recharge_order MODIFY COLUMN status VARCHAR(20) NOT NULL"
            );
            log.info("alipay_recharge_order.status 已兼容 REFUNDED（VARCHAR）");
        } catch (Exception ex) {
            log.warn("alipay_recharge_order.status 列迁移跳过或已是最新: {}", ex.getMessage());
        }
    }
}
