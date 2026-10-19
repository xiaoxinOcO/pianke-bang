package com.campus.support;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.campus.config.AliyunOssProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * 把 data:image;base64 存到阿里云 OSS，返回可访问 URL。
 * 未配齐凭证时原样返回，避免本地开发中断。
 */
@Component
public class OssImageStore {

    private static final Logger log = LoggerFactory.getLogger(OssImageStore.class);
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String CHAT_IMAGE_PREFIX = "[CHAT_IMAGE]";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AliyunOssProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean warnedNotReady;

    public OssImageStore(AliyunOssProperties properties) {
        this.properties = properties;
    }

    public boolean ready() {
        return properties.isEnabled()
                && notBlank(properties.getEndpoint())
                && notBlank(properties.getBucket())
                && notBlank(properties.getAccessKeyId())
                && notBlank(properties.getAccessKeySecret());
    }

    /** data URL 则上传；http(s) 或未配置 OSS 时原样返回。 */
    public String storeDataUrl(String raw, String folder) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
            return value;
        }
        if (!value.startsWith("data:image/")) {
            return value;
        }
        if (!ready()) {
            warnNotReady();
            return value;
        }
        ParsedImage parsed = parseDataUrl(value);
        String key = objectKey(folder, parsed.extension());
        upload(key, parsed.bytes(), parsed.contentType());
        return publicUrl(key);
    }

    public String rewriteChatContent(String content) {
        if (content == null || !content.startsWith(CHAT_IMAGE_PREFIX)) {
            return content;
        }
        String json = content.substring(CHAT_IMAGE_PREFIX.length());
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!(node instanceof ObjectNode objectNode)) {
                return content;
            }
            JsonNode imageNode = objectNode.get("image");
            if (imageNode == null || !imageNode.isTextual()) {
                return content;
            }
            String stored = storeDataUrl(imageNode.asText(), "chat");
            objectNode.put("image", stored);
            return CHAT_IMAGE_PREFIX + objectMapper.writeValueAsString(objectNode);
        } catch (Exception ex) {
            throw new IllegalArgumentException("聊天图片保存失败");
        }
    }

    private void upload(String key, byte[] bytes, String contentType) {
        String endpoint = properties.getEndpoint().trim();
        OSS client = new OSSClientBuilder().build(
                endpoint,
                properties.getAccessKeyId().trim(),
                properties.getAccessKeySecret().trim()
        );
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(bytes.length);
            meta.setContentType(contentType);
            meta.setCacheControl("public, max-age=31536000");
            client.putObject(new PutObjectRequest(
                    properties.getBucket().trim(),
                    key,
                    new ByteArrayInputStream(bytes),
                    meta
            ));
        } catch (Exception ex) {
            log.warn("OSS 上传失败 key={}", key, ex);
            throw new IllegalStateException("图片上传失败，请稍后重试");
        } finally {
            client.shutdown();
        }
    }

    private String publicUrl(String key) {
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        if (!base.isEmpty()) {
            return stripSlash(base) + "/" + key;
        }
        String endpoint = properties.getEndpoint().trim();
        String host = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + properties.getBucket().trim() + "." + host + "/" + key;
    }

    private String objectKey(String folder, String extension) {
        String prefix = properties.getKeyPrefix() == null ? "campus" : properties.getKeyPrefix().trim();
        if (prefix.isEmpty()) {
            prefix = "campus";
        }
        String safeFolder = folder == null || folder.isBlank() ? "misc" : folder.trim().toLowerCase(Locale.ROOT);
        return prefix + "/" + safeFolder + "/" + LocalDate.now().format(DAY) + "/" + UUID.randomUUID() + "." + extension;
    }

    private ParsedImage parseDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).contains(";base64")) {
            throw new IllegalArgumentException("图片格式不正确");
        }
        String header = dataUrl.substring(5, comma);
        String mime = header.split(";", 2)[0].toLowerCase(Locale.ROOT);
        if (!mime.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("图片数据无效");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片需小于 8MB");
        }
        return new ParsedImage(bytes, mime, extensionOf(mime));
    }

    private static String extensionOf(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private void warnNotReady() {
        if (!warnedNotReady) {
            warnedNotReady = true;
            log.info("OSS 未配齐（endpoint/bucket/AK），图片仍写入数据库");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record ParsedImage(byte[] bytes, String contentType, String extension) {
    }
}
