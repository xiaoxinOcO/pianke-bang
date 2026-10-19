package com.campus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtService {

    private static final String SMS_PICK_TYP = "sms-pick";
    /** 多账号择一登录：短信已验证后短时有效 */
    private static final long SMS_PICK_EXPIRATION_MS = 300_000;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:7200000}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String sessionId) {
        return generateToken(username, sessionId, null, LoginChannel.WEB);
    }

    public String generateToken(String username, String sessionId, Long userId) {
        return generateToken(username, sessionId, userId, LoginChannel.WEB);
    }

    public String generateToken(String username, String sessionId, Long userId, String channel) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put("sid", sessionId);
        claims.put("ch", LoginChannel.normalize(channel));
        if (userId != null) {
            claims.put("uid", userId);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String extractSessionId(String token) {
        Object raw = parseClaims(token).get("sid");
        return raw == null ? "" : String.valueOf(raw);
    }

    /** 旧 token 无 ch 时按网页端处理 */
    public String extractChannel(String token) {
        Object raw = parseClaims(token).get("ch");
        return LoginChannel.normalize(raw == null ? "" : String.valueOf(raw));
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object raw = parseClaims(token).get("uid");
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 短信登录多账号：验证码已校验通过后签发，用于第二步择一账号换正式 JWT。
     */
    public String generateSmsPickToken(String normalizedPhone, java.util.List<Long> userIds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + SMS_PICK_EXPIRATION_MS);
        String uids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", SMS_PICK_TYP);
        claims.put("phone", normalizedPhone);
        claims.put("uids", uids);

        return Jwts.builder()
                .claims(claims)
                .subject(SMS_PICK_TYP)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public SmsPickPayload parseSmsPickToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("请选择要登录的账号");
        }
        Claims c;
        try {
            c = parseClaims(token.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("登录凭证无效或已过期，请重新获取验证码");
        }
        if (c.getExpiration() == null || !c.getExpiration().after(new Date())) {
            throw new IllegalArgumentException("登录凭证已过期，请重新获取验证码");
        }
        Object typ = c.get("typ");
        if (typ == null || !SMS_PICK_TYP.equals(String.valueOf(typ))) {
            throw new IllegalArgumentException("登录凭证无效");
        }
        String phone = c.get("phone", String.class);
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("登录凭证无效");
        }
        String uidsRaw = c.get("uids", String.class);
        if (uidsRaw == null || uidsRaw.isBlank()) {
            throw new IllegalArgumentException("登录凭证无效");
        }
        Set<Long> ids = Arrays.stream(uidsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("登录凭证无效");
        }
        return new SmsPickPayload(phone.trim(), ids);
    }

    public record SmsPickPayload(String normalizedPhone, Set<Long> allowedUserIds) {
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
