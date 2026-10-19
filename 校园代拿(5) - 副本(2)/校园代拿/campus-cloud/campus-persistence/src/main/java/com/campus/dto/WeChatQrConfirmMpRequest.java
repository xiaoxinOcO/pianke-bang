package com.campus.dto;

/** 网页扫码会话：小程序内授权手机号后确认电脑端登录 */
public class WeChatQrConfirmMpRequest {
    private String sessionId;
    private String loginCode;
    private String phoneCode;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLoginCode() {
        return loginCode;
    }

    public void setLoginCode(String loginCode) {
        this.loginCode = loginCode;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public void setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
    }
}
