package com.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seata AT 模式依赖 undo_log 表；Flyway 未启用时在此自动建表，避免发布/钱包事务 500。
 */
@Component
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
public class SeataUndoLogInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeataUndoLogInitializer.class);

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `undo_log`
            (
                `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
                `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
                `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
                `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
                `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
                `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
                `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
                UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
            ) ENGINE = InnoDB
              DEFAULT CHARSET = utf8mb4 COMMENT ='AT transaction mode undo table'
            """;

    private final JdbcTemplate jdbcTemplate;

    public SeataUndoLogInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(DDL);
            log.info("Seata undo_log 表已就绪");
        } catch (Exception ex) {
            log.error("Seata undo_log 表初始化失败，钱包/分布式事务可能不可用: {}", ex.getMessage());
        }
    }
}
