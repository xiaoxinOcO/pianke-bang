package com.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 将 user_account 自增主键下限抬高，避免新注册用户 id 从个位数开始。
 * 每次启动按 max(id)+1 与配置下限取较大者对齐 AUTO_INCREMENT（MySQL）。
 */
@Component
public class UserAccountAutoIncrementAligner {

    private static final Logger log = LoggerFactory.getLogger(UserAccountAutoIncrementAligner.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final long idMin;

    public UserAccountAutoIncrementAligner(
            JdbcTemplate jdbcTemplate,
            Environment environment,
            @Value("${app.user-account.id-min:1000000}") long idMin
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.idMin = Math.max(1L, idMin);
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void alignUserAccountAutoIncrement() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("mysql")) {
            return;
        }
        try {
            Long max = jdbcTemplate.queryForObject("SELECT MAX(id) FROM user_account", Long.class);
            long m = max == null ? 0L : max;
            long next = Math.max(idMin, m + 1);
            jdbcTemplate.execute("ALTER TABLE user_account AUTO_INCREMENT = " + next);
            log.info("user_account 下一自增 id 已对齐为 {}（配置下限 {}）", next, idMin);
        } catch (Exception e) {
            log.warn("未能调整 user_account AUTO_INCREMENT: {}", e.getMessage());
        }
    }
}
