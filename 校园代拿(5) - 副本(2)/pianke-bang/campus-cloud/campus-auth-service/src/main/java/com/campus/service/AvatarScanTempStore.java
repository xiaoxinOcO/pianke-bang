package com.campus.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 data:image 头像短暂挂成公网可拉取资源，供阿里云 ScanImage 下载。
 */
@Component
public class AvatarScanTempStore {

    private static final long TTL_SECONDS = 120;

    public record TempImage(byte[] bytes, String contentType, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, TempImage> store = new ConcurrentHashMap<>();

    public String put(byte[] bytes, String contentType) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        String type = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
        store.put(token, new TempImage(bytes, type, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public TempImage get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TempImage image = store.get(token);
        if (image == null) {
            return null;
        }
        if (image.expired()) {
            store.remove(token);
            return null;
        }
        return image;
    }

    public void remove(String token) {
        if (token != null) {
            store.remove(token);
        }
    }

    private void purgeExpired() {
        if (store.size() < 64) {
            return;
        }
        Instant now = Instant.now();
        Iterator<Map.Entry<String, TempImage>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TempImage> e = it.next();
            if (e.getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }
}
