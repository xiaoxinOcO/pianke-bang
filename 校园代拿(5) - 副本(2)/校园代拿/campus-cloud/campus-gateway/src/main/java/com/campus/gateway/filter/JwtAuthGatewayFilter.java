package com.campus.gateway.filter;

import com.campus.config.ServiceConstants;
import com.campus.security.InternalTokenSupport;
import com.campus.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ParameterizedTypeReference<Map<String, Object>> SESSION_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private static final Duration SESSION_VALIDATE_TIMEOUT = Duration.ofSeconds(5);
    /** 单机部署默认直连 auth，避免依赖 Nacos 负载均衡查找实例 */
    private static final String DEFAULT_AUTH_VALIDATE_BASE = "http://127.0.0.1:8101";

    private static final List<String> STRIP_AUTH_HEADERS = List.of(
            ServiceConstants.HEADER_USERNAME,
            ServiceConstants.HEADER_USERNAME_ENCODING,
            ServiceConstants.HEADER_USER_ID,
            ServiceConstants.HEADER_SESSION_ID,
            ServiceConstants.HEADER_INTERNAL_TOKEN,
            "X-Username",
            "X-User-Id"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/sms/",
            "/api/auth/forgot-password/",
            "/api/auth/login-by-sms",
            "/api/auth/wechat/",
            "/api/auth/avatar-scan-temp/",
            "/api/notices",
            "/api/coupons/activities",
            "/api/hall-activities",
            "/api/tasks/hall-stats",
            "/api/wallet/alipay/notify",
            "/api/wallet/alipay/return"
    );

    /** auth-service 自行校验 JWT/会话，网关不再二次 validate-session，避免刷新时 /me 误报 401 */
    private static final Set<String> AUTH_SELF_VALIDATE_PREFIXES = Set.of(
            "/api/auth/me",
            "/api/auth/offline",
            "/api/auth/profile",
            "/api/auth/password",
            "/api/auth/rider/",
            "/api/auth/users/"
    );

    private final JwtService jwtService;
    private final InternalTokenSupport internalTokenSupport;
    private final SessionValidateCache sessionValidateCache;
    private final WebClient authWebClient;
    private final WebClient directAuthWebClient;
    private final String authValidateBaseUrl;

    public JwtAuthGatewayFilter(
            JwtService jwtService,
            InternalTokenSupport internalTokenSupport,
            SessionValidateCache sessionValidateCache,
            WebClient.Builder loadBalancedWebClientBuilder,
            @Value("${campus.gateway.auth-validate-base-url:}") String authValidateBaseUrl
    ) {
        this.jwtService = jwtService;
        this.internalTokenSupport = internalTokenSupport;
        this.sessionValidateCache = sessionValidateCache;
        this.authWebClient = loadBalancedWebClientBuilder.build();
        String configured = authValidateBaseUrl == null ? "" : authValidateBaseUrl.trim();
        // YAML/@Value 对含冒号的 URL 可能被截成 "http"，必须兜底
        if (configured.isBlank()
                || (!configured.startsWith("http://") && !configured.startsWith("https://"))) {
            log.warn("Invalid campus.gateway.auth-validate-base-url='{}', fallback to {}",
                    configured, DEFAULT_AUTH_VALIDATE_BASE);
            configured = DEFAULT_AUTH_VALIDATE_BASE;
        }
        this.authValidateBaseUrl = configured;
        this.directAuthWebClient = WebClient.builder().build();
        log.info("Gateway session validate base-url={}", this.authValidateBaseUrl);
        if (!internalTokenSupport.isConfigured()) {
            log.warn("app.internal.token 未配置：下游将不信任用户头，/internal 调用会失败");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = stripClientAuthHeaders(exchange.getRequest());
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String path = request.getURI().getPath();
        // 内部接口只允许服务间直连，禁止经公网网关暴露
        if (path.startsWith("/internal/")) {
            return forbidden(exchange, "内部接口不可通过网关访问");
        }

        ServerWebExchange stripped = exchange.mutate().request(request).build();

        if (isPublic(path, request.getMethod())) {
            // 活动列表：未登录可看；已登录则带用户信息，便于返回 claimed 状态
            if (path.startsWith("/api/coupons/activities") || path.startsWith("/api/hall-activities")) {
                String optionalToken = resolveToken(request);
                if (optionalToken != null && !optionalToken.isBlank() && jwtService.isTokenValid(optionalToken)) {
                    return authenticateAndForward(stripped, chain, request, optionalToken);
                }
            }
            return chain.filter(stripped);
        }

        if (isAuthSelfValidated(path)) {
            // auth 自校验路径：仍附带内部 token，便于服务识别来自网关
            if (internalTokenSupport.isConfigured()) {
                ServerHttpRequest withInternal = request.mutate()
                        .header(ServiceConstants.HEADER_INTERNAL_TOKEN, internalTokenSupport.tokenValue())
                        .build();
                return chain.filter(stripped.mutate().request(withInternal).build());
            }
            return chain.filter(stripped);
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "未登录或凭证缺失");
        }
        if (!jwtService.isTokenValid(token)) {
            return unauthorized(exchange, "登录态无效或已过期");
        }

        return authenticateAndForward(stripped, chain, request, token);
    }

    private static ServerHttpRequest stripClientAuthHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            for (String name : STRIP_AUTH_HEADERS) {
                headers.remove(name);
            }
        }).build();
    }

    private Mono<Void> authenticateAndForward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request,
            String token
    ) {
        String path = request.getURI().getPath();
        String username = jwtService.extractUsername(token);
        String sessionId = jwtService.extractSessionId(token);
        String channel = jwtService.extractChannel(token);
        Long userId = jwtService.extractUserId(token);

        return validateSession(username, sessionId, channel)
                .onErrorResume(ex -> {
                    log.error("validate-session failed path={} cause={}", path, ex.toString());
                    return Mono.error(new AuthValidateException(ex));
                })
                .flatMap(session -> {
                    boolean valid = isTruthy(session.get("valid"));
                    if (!valid) {
                        if (Boolean.FALSE.equals(session.get("enabled")) || "false".equalsIgnoreCase(String.valueOf(session.get("enabled")))) {
                            return unauthorized(exchange, "账号已被禁用，请联系管理员");
                        }
                        return unauthorized(exchange, "账号已在其他设备登录，请重新登录");
                    }

                    Long resolvedUserId = userId;
                    Object sessionUserId = session.get("userId");
                    if (sessionUserId != null) {
                        try {
                            resolvedUserId = Long.parseLong(String.valueOf(sessionUserId));
                        } catch (NumberFormatException ignored) {
                            // keep uid from JWT
                        }
                    }

                    EncodedUsername encodedUsername = encodeUsernameHeader(username);

                    ServerHttpRequest.Builder builder = request.mutate()
                            .header(ServiceConstants.HEADER_USERNAME, encodedUsername.value())
                            .header(ServiceConstants.HEADER_SESSION_ID, sessionId == null ? "" : sessionId);
                    if (internalTokenSupport.isConfigured()) {
                        builder.header(ServiceConstants.HEADER_INTERNAL_TOKEN, internalTokenSupport.tokenValue());
                    }
                    if (encodedUsername.encoding() != null) {
                        builder.header(ServiceConstants.HEADER_USERNAME_ENCODING, encodedUsername.encoding());
                    }
                    if (resolvedUserId != null) {
                        builder.header(ServiceConstants.HEADER_USER_ID, String.valueOf(resolvedUserId));
                    }
                    ServerHttpRequest mutated = builder.build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(ex -> {
                    if (ex instanceof AuthValidateException) {
                        return unauthorized(exchange, "认证服务不可用");
                    }
                    String msg = ex.getMessage() == null ? "" : ex.getMessage();
                    if (msg.contains("Unable to find instance") || msg.contains("SERVICE_UNAVAILABLE")) {
                        log.error("downstream missing for path={} cause={}", path, ex.toString());
                        return serviceUnavailable(exchange, "业务服务暂不可用（请检查 wallet/chat 等是否已启动）");
                    }
                    return Mono.error(ex);
                });
    }

    private Mono<Map<String, Object>> validateSession(String username, String sessionId, String channel) {
        String cacheKey = SessionValidateCache.key(username, sessionId, channel);
        return sessionValidateCache.get(cacheKey)
                .switchIfEmpty(Mono.defer(() -> fetchSession(username, sessionId, channel)
                        .flatMap(body -> sessionValidateCache.put(cacheKey, body).thenReturn(body))));
    }

    private Mono<Map<String, Object>> fetchSession(String username, String sessionId, String channel) {
        String base = authValidateBaseUrl.replaceAll("/+$", "");
        String uri = base + "/internal/auth/validate-session";
        Map<String, String> body = new HashMap<>();
        body.put("username", username == null ? "" : username);
        body.put("sessionId", sessionId == null ? "" : sessionId);
        body.put("channel", channel == null ? "" : channel);

        // 单机优先直连；失败再尝试服务名（Nacos LB）
        return callValidate(directAuthWebClient, uri, body)
                .onErrorResume(ex -> {
                    log.warn("direct auth validate failed, fallback to lb://{}: {}", ServiceConstants.AUTH_SERVICE, ex.toString());
                    String lbUri = "http://" + ServiceConstants.AUTH_SERVICE + "/internal/auth/validate-session";
                    return callValidate(authWebClient, lbUri, body);
                });
    }

    private Mono<Map<String, Object>> callValidate(WebClient client, String uri, Map<String, String> body) {
        WebClient.RequestBodySpec spec = client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON);
        if (internalTokenSupport.isConfigured()) {
            spec = spec.header(ServiceConstants.HEADER_INTERNAL_TOKEN, internalTokenSupport.tokenValue());
        }
        return spec.bodyValue(body)
                .retrieve()
                .bodyToMono(SESSION_RESPONSE)
                .timeout(SESSION_VALIDATE_TIMEOUT);
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

    private static final String USERNAME_ENCODING_BASE64 = "b64";

    private static EncodedUsername encodeUsernameHeader(String username) {
        if (username == null || username.isBlank()) {
            return new EncodedUsername(username, null);
        }
        String trimmed = username.trim();
        if (!trimmed.chars().anyMatch(ch -> ch > 127)) {
            return new EncodedUsername(trimmed, null);
        }
        return new EncodedUsername(
                Base64.getEncoder().encodeToString(trimmed.getBytes(StandardCharsets.UTF_8)),
                USERNAME_ENCODING_BASE64
        );
    }

    private record EncodedUsername(String value, String encoding) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String path = request.getURI().getPath();
        if (path != null && path.startsWith("/ws")) {
            String queryToken = request.getQueryParams().getFirst("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return "";
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (HttpMethod.GET.equals(method) && ("/api/tasks".equals(path) || "/api/tasks/".equals(path))) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAuthSelfValidated(String path) {
        return AUTH_SELF_VALIDATE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, message);
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = JSON.writeValueAsBytes(Map.of("message", message));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            byte[] bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    private static final class AuthValidateException extends RuntimeException {
        private AuthValidateException(Throwable cause) {
            super(cause);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
