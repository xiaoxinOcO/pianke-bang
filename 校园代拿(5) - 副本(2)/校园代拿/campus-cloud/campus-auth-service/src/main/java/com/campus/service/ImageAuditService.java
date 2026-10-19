package com.campus.service;

import com.aliyun.imageaudit20191230.models.ScanImageAdvanceRequest;
import com.aliyun.imageaudit20191230.models.ScanImageResponse;
import com.aliyun.imageaudit20191230.models.ScanImageResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.campus.config.AliyunImageAuditProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云视觉智能 imageaudit：优先 {@code scanImageAdvance}，用本地流上传，
 * 支持 data:image / 本地可读的 http(s) URL，无需公网临时地址。
 * <ul>
 *   <li>block → 拒绝提交</li>
 *   <li>pass → 自动生效</li>
 *   <li>review / 机审不可用 / 未启用 → 人工待审</li>
 * </ul>
 */
@Service
public class ImageAuditService {

    private static final Logger log = LoggerFactory.getLogger(ImageAuditService.class);
    private static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    public enum Verdict {
        /** 明确违规，应拒绝 */
        BLOCK,
        /** 机审通过，直接生效 */
        PASS,
        /** 不确定、未启用或机审失败降级，进入人工待审 */
        NEED_HUMAN
    }

    public record AuditOutcome(Verdict verdict, String detail) {
    }

    private final AliyunImageAuditProperties properties;
    private final ObjectProvider<com.aliyun.imageaudit20191230.Client> clientProvider;

    public ImageAuditService(
            AliyunImageAuditProperties properties,
            ObjectProvider<com.aliyun.imageaudit20191230.Client> clientProvider
    ) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && clientProvider.getIfAvailable() != null;
    }

    /**
     * 扫描头像；违规抛 {@link IllegalArgumentException}；返回 PASS / NEED_HUMAN。
     */
    public AuditOutcome requireAvatarAudit(String avatarPayload) {
        AuditOutcome outcome = auditAvatar(avatarPayload);
        if (outcome.verdict() == Verdict.BLOCK) {
            throw new IllegalArgumentException(
                    outcome.detail() == null || outcome.detail().isBlank()
                            ? "头像含违规内容，请更换后重试"
                            : outcome.detail()
            );
        }
        return outcome;
    }

    public AuditOutcome auditAvatar(String avatarPayload) {
        if (!properties.isEnabled()) {
            return new AuditOutcome(Verdict.NEED_HUMAN, "image-audit disabled");
        }
        com.aliyun.imageaudit20191230.Client client = clientProvider.getIfAvailable();
        if (client == null) {
            return failOpenOrBlock("图片审核客户端未初始化");
        }
        String raw = avatarPayload == null ? "" : avatarPayload.trim();
        if (raw.isBlank()) {
            return new AuditOutcome(Verdict.NEED_HUMAN, "empty avatar");
        }

        try (InputStream imageStream = openAvatarStream(raw)) {
            ScanImageAdvanceRequest.ScanImageAdvanceRequestTask task =
                    new ScanImageAdvanceRequest.ScanImageAdvanceRequestTask();
            task.setDataId("avatar-" + UUID.randomUUID().toString().replace("-", ""));
            task.setImageTimeMillisecond(1L);
            task.setInterval(1);
            task.setMaxFrames(1);
            task.setImageURLObject(imageStream);

            ScanImageAdvanceRequest request = new ScanImageAdvanceRequest()
                    .setTask(List.of(task))
                    .setScene(properties.resolveSceneList());

            ScanImageResponse response = client.scanImageAdvance(request, new RuntimeOptions());
            return interpret(response);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("阿里云图片审核调用失败: {}", ex.getMessage());
            return failOpenOrBlock("图片审核服务暂时不可用: " + ex.getMessage());
        }
    }

    private InputStream openAvatarStream(String raw) throws Exception {
        if (raw.startsWith("data:image")) {
            DecodedDataUrl decoded = decodeDataUrl(raw);
            return new ByteArrayInputStream(decoded.bytes());
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            URL url = URI.create(raw).toURL();
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "campus-auth-image-audit/1.0");
            InputStream in = conn.getInputStream();
            // 先读入内存，便于限制大小并关闭连接；再交给 Advance 上传
            byte[] bytes = in.readAllBytes();
            in.close();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("头像图片为空");
            }
            if (bytes.length > MAX_AVATAR_BYTES) {
                throw new IllegalArgumentException("头像过大，请压缩后重试");
            }
            return new ByteArrayInputStream(bytes);
        }
        throw new IllegalArgumentException("头像不是可访问的 URL 或 data:image");
    }

    private AuditOutcome interpret(ScanImageResponse response) {
        if (response == null || response.getBody() == null) {
            return failOpenOrBlock("图片审核无返回结果");
        }
        ScanImageResponseBody body = response.getBody();
        if (body.getData() == null || body.getData().getResults() == null) {
            return failOpenOrBlock("图片审核结果为空");
        }

        boolean anyBlock = false;
        boolean anyReview = false;
        String blockLabel = null;
        Float blockRate = null;

        for (ScanImageResponseBody.ScanImageResponseBodyDataResults result : body.getData().getResults()) {
            if (result.getSubResults() == null) {
                continue;
            }
            for (ScanImageResponseBody.ScanImageResponseBodyDataResultsSubResults sub : result.getSubResults()) {
                String suggestion = sub.getSuggestion() == null ? "" : sub.getSuggestion().trim().toLowerCase(Locale.ROOT);
                String label = sub.getLabel() == null ? "" : sub.getLabel().trim().toLowerCase(Locale.ROOT);
                // ad 场景：二维码 / 小程序码一律拒绝作头像（即使 suggestion 不是 block）
                if ("qrcode".equals(label) || "programcode".equals(label)) {
                    anyBlock = true;
                    blockLabel = label;
                    blockRate = sub.getRate();
                    continue;
                }
                if ("block".equals(suggestion)) {
                    anyBlock = true;
                    blockLabel = sub.getLabel();
                    blockRate = sub.getRate();
                } else if ("review".equals(suggestion)) {
                    anyReview = true;
                }
            }
        }

        if (anyBlock) {
            String detail;
            if ("qrcode".equalsIgnoreCase(blockLabel) || "programcode".equalsIgnoreCase(blockLabel)) {
                detail = "头像不能使用二维码或小程序码，请更换普通照片";
            } else {
                detail = "头像含违规内容，请更换后重试";
                if (blockLabel != null && !blockLabel.isBlank()) {
                    detail = "头像含违规内容（" + blockLabel
                            + (blockRate != null ? ", " + String.format(Locale.ROOT, "%.0f", blockRate) + "%" : "")
                            + "），请更换后重试";
                }
            }
            log.info("头像机审拒绝: label={} rate={}", blockLabel, blockRate);
            return new AuditOutcome(Verdict.BLOCK, detail);
        }

        if (anyReview) {
            log.info("头像机审不确定，转人工: requestId={}", body.getRequestId());
            return new AuditOutcome(Verdict.NEED_HUMAN, "review");
        }

        log.info("头像机审通过，自动生效: requestId={}", body.getRequestId());
        return new AuditOutcome(Verdict.PASS, "pass");
    }

    private AuditOutcome failOpenOrBlock(String reason) {
        if (properties.isFailOpen()) {
            log.warn("图片审核降级为人工待审: {}", reason);
            return new AuditOutcome(Verdict.NEED_HUMAN, reason);
        }
        return new AuditOutcome(Verdict.BLOCK, "头像审核失败，请稍后再试");
    }

    private record DecodedDataUrl(byte[] bytes, String contentType) {
    }

    private static DecodedDataUrl decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("头像格式不正确");
        }
        String meta = dataUrl.substring(5, comma);
        String payload = dataUrl.substring(comma + 1);
        String contentType = "image/jpeg";
        int semi = meta.indexOf(';');
        if (semi > 0) {
            contentType = meta.substring(0, semi).trim();
        } else if (!meta.isBlank() && !meta.contains("base64")) {
            contentType = meta.trim();
        }
        if (!meta.toLowerCase(Locale.ROOT).contains("base64")) {
            throw new IllegalArgumentException("仅支持 base64 头像数据");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("头像数据为空");
            }
            if (bytes.length > MAX_AVATAR_BYTES) {
                throw new IllegalArgumentException("头像过大，请压缩后重试");
            }
            return new DecodedDataUrl(bytes, contentType);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("头像解码失败");
        }
    }
}
