package com.campus.config;

import com.campus.repository.UserAccountRepository;
import com.campus.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public WebSocketConfig(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://8.163.91.106:*",
                        "http://47.86.165.82",
                        "http://47.86.165.82:*",
                        "https://api.xiaoxin1.xyz",
                        "https://*.xiaoxin1.xyz",
                        "http://api.xiaoxin1.xyz",
                        "http://*.xiaoxin1.xyz"
                )
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        String token = extractToken(request);
                        String username = resolveUsernameFromToken(token);
                        if (username == null || username.isBlank()) {
                            // 未登录也允许连接，但只能拿到匿名 principal（不会收到 user 推送）
                            return () -> "anon-" + UUID.randomUUID();
                        }
                        return () -> username;
                    }
                })
                .withSockJS();
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            String t = servletReq.getServletRequest().getParameter("token");
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
            String auth = servletReq.getServletRequest().getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                return auth.substring("Bearer ".length()).trim();
            }
        }
        return "";
    }

    private String resolveUsernameFromToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            if (!jwtService.isTokenValid(token)) {
                return "";
            }
            String username = jwtService.extractUsername(token);
            String sessionId = jwtService.extractSessionId(token);
            if (username == null || username.isBlank()) {
                return "";
            }
            var userOpt = userAccountRepository.findByUsername(username);
            if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
                return "";
            }
            var user = userOpt.get();
            String sid = sessionId == null ? "" : sessionId.trim();
            boolean ok = sessionMatches(user.getActiveSessionId(), sid)
                    || sessionMatches(user.getActiveMpSessionId(), sid)
                    || sessionMatches(user.getActiveRiderSessionId(), sid);
            if (!ok) {
                return "";
            }
            return username.trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean sessionMatches(String active, String sessionId) {
        return active != null && !active.isBlank() && active.trim().equals(sessionId);
    }
}

