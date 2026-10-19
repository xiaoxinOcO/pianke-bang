package com.campus.dto;

public class AdminAiAutoAuditEnabledResponse {
    private Boolean enabled;

    public AdminAiAutoAuditEnabledResponse() {
    }

    public AdminAiAutoAuditEnabledResponse(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

