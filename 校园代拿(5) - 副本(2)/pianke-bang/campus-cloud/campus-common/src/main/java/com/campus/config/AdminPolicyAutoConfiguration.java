package com.campus.config;

import com.campus.repository.UserAccountRepository;
import com.campus.security.AdminPolicy;
import com.campus.security.AdminRoleSeedRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 仅在有数据源的业务服务中注册管理员策略；网关无 DataSource，不会加载。
 */
@AutoConfiguration
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        CampusPersistenceAutoConfiguration.class
})
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminPolicyAutoConfiguration {

    @Bean
    public AdminPolicy adminPolicy(UserAccountRepository userAccountRepository) {
        return new AdminPolicy(userAccountRepository);
    }

    @Bean
    public AdminRoleSeedRunner adminRoleSeedRunner(
            UserAccountRepository userAccountRepository,
            AdminProperties adminProperties
    ) {
        return new AdminRoleSeedRunner(userAccountRepository, adminProperties);
    }
}
