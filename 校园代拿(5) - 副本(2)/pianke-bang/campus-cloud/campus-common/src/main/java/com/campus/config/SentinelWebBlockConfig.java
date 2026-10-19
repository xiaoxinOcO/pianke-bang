package com.campus.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微服务被 Sentinel 限流/降级时的统一 JSON 响应。
 */
@Configuration
public class SentinelWebBlockConfig {

    @Bean
    public BlockExceptionHandler sentinelBlockExceptionHandler() {
        return (HttpServletRequest request, HttpServletResponse response, String resourceName, BlockException e)
                -> {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\"}");
        };
    }
}
