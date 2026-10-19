package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python 向量检索服务（agent/rag/vector/retrieve_server.py）。
 * 默认开启；服务不可用时 {@link com.campus.rag.RagKnowledgeStore} 自动降级关键词 RAG。
 */
@Component
@ConfigurationProperties(prefix = "app.rag.vector")
public class RagVectorProperties {
    /** 是否启用向量检索（默认 true；可用 CAMPUS_RAG_VECTOR_ENABLED=false 关闭） */
    private boolean enabled = true;
    /** 检索服务根地址，如 http://127.0.0.1:8765 */
    private String baseUrl = "http://127.0.0.1:8765";
    /** HTTP 超时（毫秒） */
    private int timeoutMs = 3000;

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

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
