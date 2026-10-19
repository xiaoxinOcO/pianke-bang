package com.campus.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关路由被 Sentinel 限流时的 JSON 降级响应。
 */
@Configuration
public class SentinelGatewayBlockConfig {

    @PostConstruct
    public void initBlockHandler() {
        BlockRequestHandler handler = (ServerWebExchange exchange, Throwable ex) ->
                ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue("{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\"}"));
        GatewayCallbackManager.setBlockHandler(handler);
    }
}
