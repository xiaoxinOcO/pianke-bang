package com.campus.dto;

/**
 * 通过注册手机号 + 短信验证码重置密码。
 * 多账号时凭 {@link #pickToken} 与 {@link #userId} 指定账号；{@link #account} 仅兼容旧客户端。
 */
public class ForgotPasswordResetRequest {
    private String phone;
    private String code;
    private String newPassword;
    /** 多账号时（旧版）：站内昵称或数字账号 ID */
    private String account;
    /** 短信验证通过后签发的短时凭证 */
    private String pickToken;
    private Long userId;

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

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
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
