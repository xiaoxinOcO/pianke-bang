package com.campus.dto;

/**
 * 使用注册手机号 + 短信验证码登录（与 {@link RegisterBySmsRequest} 共用短信通道 {@link com.campus.service.SmsCodeService}）。
 */
public class LoginBySmsRequest {
    private String phone;
    private String code;
    /** web | mp，默认 web；小程序须传 mp */
    private String clientChannel;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getClientChannel() {
        return clientChannel;
    }

    public void setClientChannel(String clientChannel) {
        this.clientChannel = clientChannel;
    }
}
