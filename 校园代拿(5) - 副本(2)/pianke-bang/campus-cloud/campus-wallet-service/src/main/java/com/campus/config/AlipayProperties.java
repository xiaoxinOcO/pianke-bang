package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.alipay")
public class AlipayProperties {

    private boolean enabled = true;
    private String appId;
    /** 需上传到支付宝开放平台「接口加签方式」的应用公钥 */
    private String appPublicKey;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String notifyUrl = "http://localhost:8080/api/wallet/alipay/notify";
    private String returnUrl = "http://localhost:8080/api/wallet/alipay/return";
    /** 支付完成后跳回的前台地址（hash 路由） */
    private String frontendReturnUrl = "http://localhost:5173/#/wallet";
    /** 任务赏金支付完成后跳回的前台地址 */
    private String frontendTaskReturnUrl = "http://localhost:5173/#/publish";
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private String format = "json";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppPublicKey() {
        return normalizeKey(appPublicKey);
    }

    public void setAppPublicKey(String appPublicKey) {
        this.appPublicKey = appPublicKey;
    }

    public String getPrivateKey() {
        return normalizeKey(privateKey);
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAlipayPublicKey() {
        return normalizeKey(alipayPublicKey);
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    private static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getFrontendReturnUrl() {
        return frontendReturnUrl;
    }

    public void setFrontendReturnUrl(String frontendReturnUrl) {
        this.frontendReturnUrl = frontendReturnUrl;
    }

    public String getFrontendTaskReturnUrl() {
        return frontendTaskReturnUrl;
    }

    public void setFrontendTaskReturnUrl(String frontendTaskReturnUrl) {
        this.frontendTaskReturnUrl = frontendTaskReturnUrl;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
