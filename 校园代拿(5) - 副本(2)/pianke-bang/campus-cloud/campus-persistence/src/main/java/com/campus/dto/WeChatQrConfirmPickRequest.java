package com.campus.dto;

public class WeChatQrConfirmPickRequest {
    private String sessionId;
    private String pickToken;
    private Long userId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPickToken() {
        return pickToken;
    }

    public void setPickToken(String pickToken) {
        this.pickToken = pickToken;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
