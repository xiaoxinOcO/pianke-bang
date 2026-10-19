package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app.aliyun.image-audit")
public class AliyunImageAuditProperties {

    /**
     * 为 true 时，用户提交新头像会先走阿里云 ScanImage；block 直接拒绝，其余进入人工待审。
     * 凭证优先环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID / SECRET，其次本配置，再回退 dypns 配置。
     */
    private boolean enabled = false;

    private String endpoint = "imageaudit.cn-shanghai.aliyuncs.com";

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /** 逗号分隔，默认 porn + ad + terrorism + live */
    private String scenes = "porn,ad,terrorism,live";

    /**
     * 公网可访问前缀，用于把 data:image 头像临时挂成 URL 供阿里云拉取。
     * 例：https://api.xiaoxin1.xyz
     */
    private String publicBaseUrl = "https://api.xiaoxin1.xyz";

    /** 调用失败时是否仍允许进入人工待审 */
    private boolean failOpen = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getScenes() {
        return scenes;
    }

    public void setScenes(String scenes) {
        this.scenes = scenes;
    }

    public List<String> resolveSceneList() {
        if (scenes == null || scenes.isBlank()) {
            return List.of("porn", "ad", "terrorism", "live");
        }
        List<String> list = Arrays.stream(scenes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        return list.isEmpty() ? List.of("porn", "ad", "terrorism", "live") : list;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
