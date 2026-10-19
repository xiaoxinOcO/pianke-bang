package com.campus.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 网关会话校验结果短缓存。Redis 不可用时当作未命中，请求仍走认证服务。
 */
@Component
public class SessionValidateCache {

    private static final Logger log = LoggerFactory.getLogger(SessionValidateCache.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Duration REDIS_OP_TIMEOUT = Duration.ofMillis(250);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReactiveStringRedisTemplate redis;
    private final boolean enabled;
    private final Duration validTtl;
    private final Duration invalidTtl;

    public SessionValidateCache(
            ReactiveStringRedisTemplate redis,
            @Value("${campus.gateway.session-cache-enabled:true}") boolean enabled,
            @Value("${campus.gateway.session-cache-ttl-seconds:15}") long ttlSeconds
    ) {
        this.redis = redis;
        this.enabled = enabled;
        long seconds = ttlSeconds < 1 ? 15 : ttlSeconds;
        this.validTtl = Duration.ofSeconds(seconds);
        this.invalidTtl = Duration.ofSeconds(Math.min(3, seconds));
    }

    public Mono<Map<String, Object>> get(String key) {
        if (!enabled || key == null || key.isBlank()) {
            return Mono.empty();
        }
        return redis.opsForValue().get(key)
                .timeout(REDIS_OP_TIMEOUT)
                .flatMap(raw -> {
                    if (raw == null || raw.isBlank()) {
                        return Mono.empty();
                    }
                    try {
                        Map<String, Object> body = JSON.readValue(raw, MAP_TYPE);
                        return body == null || body.isEmpty() ? Mono.empty() : Mono.just(body);
                    } catch (Exception ex) {
                        return Mono.empty();
                    }
                })
                .onErrorResume(ex -> {
                    log.debug("session cache get skipped: {}", ex.toString());
                    return Mono.empty();
                });
    }

    public Mono<Void> put(String key, Map<String, Object> body) {
        if (!enabled || key == null || key.isBlank() || body == null || body.isEmpty()) {
            return Mono.empty();
        }
        final String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (Exception ex) {
            return Mono.empty();
        }
        boolean valid = isTruthy(body.get("valid"));
        Duration ttl = valid ? validTtl : invalidTtl;
        return redis.opsForValue().set(key, json, ttl)
                .timeout(REDIS_OP_TIMEOUT)
                .onErrorResume(ex -> {
                    log.debug("session cache put skipped: {}", ex.toString());
                    return Mono.just(false);
                })
                .then();
    }

    static String key(String username, String sessionId, String channel) {
        return "campus:sess:"
                + safe(username) + ":"
                + safe(channel) + ":"
                + safe(sessionId);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim().replace(':', '_');
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }
}
