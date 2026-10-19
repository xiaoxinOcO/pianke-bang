package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.wechat")
public class WeChatProperties {

    private final Mp mp = new Mp();
    private final Web web = new Web();

    public Mp getMp() {
        return mp;
    }

    public Web getWeb() {
        return web;
    }

    public static class Mp {
        private boolean enabled = true;
        private String appId = "";
        private String appSecret = "";
        /** 小程序码打开版本：release / trial / develop（未正式上线时用 trial） */
        private String envVersion = "trial";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId == null ? "" : appId.trim();
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret == null ? "" : appSecret.trim();
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getEnvVersion() {
            String v = envVersion == null ? "" : envVersion.trim().toLowerCase();
            if ("release".equals(v) || "develop".equals(v) || "trial".equals(v)) {
                return v;
            }
            return "trial";
        }

        public void setEnvVersion(String envVersion) {
            this.envVersion = envVersion;
        }

        public boolean isConfigured() {
            return enabled && !getAppId().isBlank() && !getAppSecret().isBlank();
        }
    }

    public static class Web {
        private boolean enabled = true;
        private String appId = "";
        private String appSecret = "";
        private String redirectUri = "";
        private String frontendBaseUrl = "";
        private long sessionTtlSeconds = 180;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId == null ? "" : appId.trim();
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret == null ? "" : appSecret.trim();
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getRedirectUri() {
            return redirectUri == null ? "" : redirectUri.trim();
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getFrontendBaseUrl() {
            return frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/$", "");
        }

        public void setFrontendBaseUrl(String frontendBaseUrl) {
            this.frontendBaseUrl = frontendBaseUrl;
        }

        public long getSessionTtlSeconds() {
            return sessionTtlSeconds <= 0 ? 180 : sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(long sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }

        public boolean isOauthConfigured() {
            return enabled
                    && !getAppId().isBlank()
                    && !getAppSecret().isBlank()
                    && !getRedirectUri().isBlank();
        }
    }
}
