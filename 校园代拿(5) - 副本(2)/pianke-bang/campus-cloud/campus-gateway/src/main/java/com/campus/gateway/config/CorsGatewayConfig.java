package com.campus.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsGatewayConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 浏览器 JSON POST 会带 Origin；未匹配时 CorsWebFilter 直接 403 且空 body
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://8.163.91.106:*",
                "http://47.86.165.82",
                "http://47.86.165.82:*",
                "https://api.xiaoxin1.xyz",
                "https://*.xiaoxin1.xyz",
                "http://api.xiaoxin1.xyz",
                "http://*.xiaoxin1.xyz"
        ));
        // PATCH：大厅活动/优惠券「隐藏·启停」等状态接口；漏了会被浏览器 CORS 预检直接 403
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // WebSocket（/ws/**）由 chat-service 的 SockJS 自行处理 CORS，避免与网关重复加头
        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }
}
