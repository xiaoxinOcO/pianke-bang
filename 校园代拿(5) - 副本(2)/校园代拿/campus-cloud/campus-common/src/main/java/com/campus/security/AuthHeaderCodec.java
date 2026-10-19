package com.campus.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网关与微服务之间用户名请求头的编解码，避免中文用户名在 HTTP Header 中乱码。
 */
public final class AuthHeaderCodec {

    public static final String ENCODING_BASE64 = "b64";

    private AuthHeaderCodec() {
    }

    public static boolean needsBase64Encoding(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return username.chars().anyMatch(ch -> ch > 127);
    }

    public static String encodeUsername(String username) {
        if (username == null || username.isBlank()) {
            return username;
        }
        String trimmed = username.trim();
        if (!needsBase64Encoding(trimmed)) {
            return trimmed;
        }
        return Base64.getEncoder().encodeToString(trimmed.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodingForUsername(String username) {
        return needsBase64Encoding(username) ? ENCODING_BASE64 : null;
    }

    public static String decodeUsername(String raw, String encoding) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (ENCODING_BASE64.equals(encoding)) {
            return decodeBase64OrFallback(trimmed);
        }
        if (encoding == null || encoding.isBlank()) {
            if (looksLikeBase64Payload(trimmed)) {
                String decoded = tryDecodeBase64(trimmed);
                if (decoded != null) {
                    return decoded;
                }
            }
        }
        return trimmed;
    }

    private static String decodeBase64OrFallback(String raw) {
        String decoded = tryDecodeBase64(raw);
        return decoded != null ? decoded : raw;
    }

    private static boolean looksLikeBase64Payload(String value) {
        if (value.length() < 4 || (value.length() % 4) != 0) {
            return false;
        }
        return value.chars().allMatch(ch ->
                (ch >= 'A' && ch <= 'Z')
                        || (ch >= 'a' && ch <= 'z')
                        || (ch >= '0' && ch <= '9')
                        || ch == '+'
                        || ch == '/'
                        || ch == '='
                        || ch == '-'
                        || ch == '_');
    }

    private static String tryDecodeBase64(String raw) {
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            String decoded = new String(bytes, StandardCharsets.UTF_8).trim();
            if (decoded.isEmpty() || decoded.indexOf('\0') >= 0) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
