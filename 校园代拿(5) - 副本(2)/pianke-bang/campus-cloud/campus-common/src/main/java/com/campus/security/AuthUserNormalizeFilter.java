package com.campus.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 有数据库的微服务：用 userId 查库写回真实 username，避免 Header 编码差异。
 * 网关无 DataSource/JdbcTemplate，不会注册此 Filter。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(JdbcTemplate.class)
public class AuthUserNormalizeFilter extends OncePerRequestFilter {

    private static final String SQL_BY_ID = "SELECT username FROM user_account WHERE id = ? LIMIT 1";
    private static final String SQL_BY_NAME = "SELECT id, username FROM user_account WHERE LOWER(username) = LOWER(?) LIMIT 1";

    private final JdbcTemplate jdbcTemplate;

    public AuthUserNormalizeFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            normalizeCurrentUser();
            filterChain.doFilter(request, response);
        } finally {
            // AuthHeaderFilter 会在 finally 中 clear；此处不重复 clear
        }
    }

    private void normalizeCurrentUser() {
        Long userId = AuthContext.getCurrentUserId();
        if (userId != null) {
            List<String> names = jdbcTemplate.query(
                    SQL_BY_ID,
                    (rs, rowNum) -> rs.getString("username"),
                    userId
            );
            if (!names.isEmpty() && names.get(0) != null && !names.get(0).isBlank()) {
                AuthContext.setCurrentUser(names.get(0).trim());
                return;
            }
        }
        String headerUsername = AuthContext.getCurrentUser();
        if (headerUsername == null || headerUsername.isBlank()) {
            return;
        }
        List<UserRow> rows = jdbcTemplate.query(
                SQL_BY_NAME,
                (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username")),
                headerUsername.trim()
        );
        if (!rows.isEmpty()) {
            UserRow row = rows.get(0);
            if (row.username() != null && !row.username().isBlank()) {
                AuthContext.setCurrentUser(row.username().trim());
            }
            AuthContext.setCurrentUserId(row.id());
        }
    }

    private record UserRow(long id, String username) {
    }
}
