package com.campus.dto;

/** 忘记密码：校验短信验证码并返回可选账号列表 */
public class ForgotPasswordVerifyRequest {
    private String phone;
    private String code;

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
}
