package com.campus.security;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部凭证：Feign / 网关转发携带，校验 /internal 与用户头可信性。
 * Bean 由 {@link com.campus.config.CampusCloudBootstrap} 注册。
 */
public class InternalTokenSupport {

    private final String token;

    public InternalTokenSupport(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(token);
    }

    public String tokenValue() {
        return token;
    }

    public boolean matches(String provided) {
        if (!isConfigured() || !StringUtils.hasText(provided)) {
            return false;
        }
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
