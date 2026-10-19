package com.campus.config;

import com.campus.security.InternalTokenSupport;
import com.campus.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class, CampusMqProperties.class, AliyunOssProperties.class})
public class CampusCloudBootstrap {

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties.getSecret(), properties.getExpirationMs());
    }

    @Bean
    public InternalTokenSupport internalTokenSupport(@Value("${app.internal.token:}") String token) {
        return new InternalTokenSupport(token);
    }
}
