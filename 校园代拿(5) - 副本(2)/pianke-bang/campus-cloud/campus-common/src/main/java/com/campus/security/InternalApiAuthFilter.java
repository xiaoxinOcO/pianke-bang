package com.campus.security;

import com.campus.config.ServiceConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * /internal/** 必须携带与配置一致的内部 token，防止公网直打服务端口。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalApiAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final InternalTokenSupport internalTokenSupport;

    public InternalApiAuthFilter(InternalTokenSupport internalTokenSupport) {
        this.internalTokenSupport = internalTokenSupport;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!internalTokenSupport.isConfigured()) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "INTERNAL_TOKEN_MISSING", "服务未配置内部凭证");
            return;
        }
        String provided = request.getHeader(ServiceConstants.HEADER_INTERNAL_TOKEN);
        if (!internalTokenSupport.matches(provided)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "INTERNAL_UNAUTHORIZED", "内部接口未授权");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeJson(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] body = JSON.writeValueAsBytes(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "code", status,
                "errorCode", errorCode,
                "message", message
        ));
        response.getOutputStream().write(body);
    }
}
