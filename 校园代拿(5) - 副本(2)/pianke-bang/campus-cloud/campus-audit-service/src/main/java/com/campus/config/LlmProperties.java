package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容 LLM 配置。当前默认对接 DeepSeek。
 */
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {
    private boolean enabled = true;
    /** DeepSeek 官方示例为 https://api.deepseek.com（请求 /chat/completions） */
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-pro";
    private String supportModel = "deepseek-v4-pro";
    private String timeoutSeconds = "120";
    /** DeepSeek reasoning：enabled / disabled */
    private boolean thinkingEnabled = true;
    /** high / medium / low 等，依官方文档 */
    private String reasoningEffort = "high";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSupportModel() {
        return supportModel;
    }

    public void setSupportModel(String supportModel) {
        this.supportModel = supportModel;
    }

    public String getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(String timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }
}
