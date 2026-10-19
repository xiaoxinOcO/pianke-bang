package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangGraph 编排服务（agent/graph/server.py）。
 * 开启后客服主链路优先走 Graph；失败则回落现有 Java Agent。
 */
@Component
@ConfigurationProperties(prefix = "app.agent.graph")
public class AgentGraphProperties {
    /** 是否启用 LangGraph 编排 */
    private boolean enabled = true;
    /** Graph 服务根地址 */
    private String baseUrl = "http://127.0.0.1:8766";
    /** HTTP 超时（毫秒） */
    private int timeoutMs = 120000;
    /** 与 Python 约定的内部令牌（可空；空则不校验） */
    private String internalToken = "";

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

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
