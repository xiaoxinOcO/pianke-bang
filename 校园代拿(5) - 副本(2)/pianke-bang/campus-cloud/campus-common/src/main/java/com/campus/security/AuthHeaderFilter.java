package com.campus.security;

import com.campus.config.ServiceConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网关鉴权通过后注入用户名/用户ID，各微服务从请求头恢复 {@link AuthContext}。
 * 仅当请求携带合法内部 token 时才信任 X-Campus-* 用户头，避免客户端伪造身份。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthHeaderFilter extends OncePerRequestFilter {

    private final InternalTokenSupport internalTokenSupport;

    public AuthHeaderFilter(InternalTokenSupport internalTokenSupport) {
        this.internalTokenSupport = internalTokenSupport;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String internalToken = request.getHeader(ServiceConstants.HEADER_INTERNAL_TOKEN);
        boolean trustUserHeaders = internalTokenSupport.matches(internalToken);

        if (trustUserHeaders) {
            String usernameRaw = request.getHeader(ServiceConstants.HEADER_USERNAME);
            if (usernameRaw != null && !usernameRaw.isBlank()) {
                String encoding = request.getHeader(ServiceConstants.HEADER_USERNAME_ENCODING);
                String username = AuthHeaderCodec.decodeUsername(usernameRaw, encoding);
                if (username != null && !username.isBlank()) {
                    AuthContext.setCurrentUser(username.trim());
                }
            }
            String userIdRaw = request.getHeader(ServiceConstants.HEADER_USER_ID);
            if (userIdRaw != null && !userIdRaw.isBlank()) {
                try {
                    AuthContext.setCurrentUserId(Long.parseLong(userIdRaw.trim()));
                } catch (NumberFormatException ignored) {
                    // ignore invalid header
                }
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }
}
