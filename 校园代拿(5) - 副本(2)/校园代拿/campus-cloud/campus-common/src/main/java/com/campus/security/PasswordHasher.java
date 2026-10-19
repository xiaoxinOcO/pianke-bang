package com.campus.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码单向哈希（BCrypt）。库中只存哈希，登录时用 matches 校验。
 * 兼容历史明文：校验通过后由调用方升级为哈希。
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isBcryptHash(stored)) {
            return encoder.matches(rawPassword, stored);
        }
        // 历史明文
        return stored.equals(rawPassword);
    }

    /** 库中仍是明文，登录成功后应升级为 BCrypt。 */
    public boolean needsRehash(String stored) {
        return stored != null && !stored.isBlank() && !isBcryptHash(stored);
    }

    public static boolean isBcryptHash(String stored) {
        return stored.startsWith("$2a$")
                || stored.startsWith("$2b$")
                || stored.startsWith("$2y$");
    }
}
