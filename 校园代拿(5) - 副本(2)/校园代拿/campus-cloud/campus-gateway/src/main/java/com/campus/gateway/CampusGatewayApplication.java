package com.campus.gateway;

import com.campus.config.CampusCloudBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * 网关不连数据库，勿扫描 com.campus（否则会拉起 JPA/Flyway）。
 */
@SpringBootApplication(
        scanBasePackages = "com.campus.gateway",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
@Import(CampusCloudBootstrap.class)
@EnableDiscoveryClient
public class CampusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusGatewayApplication.class, args);
    }
}
