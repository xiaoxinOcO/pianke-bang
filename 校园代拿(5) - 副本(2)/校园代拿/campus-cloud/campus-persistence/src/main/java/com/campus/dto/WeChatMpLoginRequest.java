package com.campus.dto;

public class WeChatMpLoginRequest {
    /** wx.login 拿到的 code */
    private String loginCode;
    /** 按钮 getPhoneNumber 返回的 code */
    private String phoneCode;

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
