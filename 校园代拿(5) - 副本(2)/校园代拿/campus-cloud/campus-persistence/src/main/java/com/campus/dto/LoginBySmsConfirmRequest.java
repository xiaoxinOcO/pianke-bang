package com.campus.dto;

/**
 * 短信登录多账号：凭 {@code pickToken} 择一账号换正式 JWT。
 */
public class LoginBySmsConfirmRequest {
    private String pickToken;
    private Long userId;
    /** web | mp，默认 web；小程序须传 mp */
    private String clientChannel;

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

    public String getClientChannel() {
        return clientChannel;
    }

    public void setClientChannel(String clientChannel) {
        this.clientChannel = clientChannel;
    }
}
