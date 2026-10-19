package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aliyun.dypns")
public class AliyunDypnsProperties {

    /**
     * 为 true 时，注册短信走阿里云号码认证（SendSmsVerifyCode / CheckSmsVerifyCode），不再写入本地 sms_verification_code 表。
     * 凭证优先读环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET；
     * 未设置时可填下方 access-key-id / access-key-secret（本地开发用，生产建议只用环境变量）。
     */
    private boolean enabled = false;

    /** 本地/IDE 调试可选；生产请用环境变量覆盖，勿提交真实密钥 */
    private String accessKeyId = "";

    /** 本地/IDE 调试可选；生产请用环境变量覆盖，勿提交真实密钥 */
    private String accessKeySecret = "";

    /** 与控制台 OpenAPI 调试一致，例如 ap-southeast-1、cn-hangzhou */
    private String region = "ap-southeast-1";

    /** 留空则由 SDK 结合 region 选接入点；仅当需要固定公网域名时再填 dypnsapi.aliyuncs.com */
    private String endpoint = "";

    /** 号码认证方案名称，控制台创建的方案 */
    private String schemeName = "";

    /** 手机号国家码，如 86 */
    private String countryCode = "86";

    private String signName = "";

    private String templateCode = "";

    /** 模板变量 JSON，验证码占位一般为 ##code## */
    private String templateParam = "{\"code\":\"##code##\",\"min\":\"5\"}";

    private Long codeLength = 6L;

    /** 验证码有效时间（秒） */
    private Long validTime = 300L;

    private Long duplicatePolicy = 1L;

    private Long interval = 60L;

    private Long codeType = 1L;

    /**
     * 仅调试用途：为 true 时接口响应里可能带明文验证码。生产必须为 false。
     */
    private boolean returnVerifyCode = false;

    /** 可选，不需要则留空 */
    private String smsUpExtendCode = "";

    /**
     * 为 true 时才在请求中带上 {@link #smsUpExtendCode}。默认 false，避免仅通过环境变量
     * {@code APP_ALIYUN_DYNS_SMS_UP_EXTEND_CODE} 误注入扩展码导致「签名或模板无效」。
     */
    private boolean sendSmsUpExtendCode = false;

    /**
     * 对应 OutId。留空时代码使用 reg-{uuid}；与 OpenAPI 完全一致时可填固定值（仅调试用，生产建议留空）。
     */
    private String outId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getSignName() {
        return signName;
    }

    public void setSignName(String signName) {
        this.signName = signName;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateParam() {
        return templateParam;
    }

    public void setTemplateParam(String templateParam) {
        this.templateParam = templateParam;
    }

    public Long getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(Long codeLength) {
        this.codeLength = codeLength;
    }

    public Long getValidTime() {
        return validTime;
    }

    public void setValidTime(Long validTime) {
        this.validTime = validTime;
    }

    public Long getDuplicatePolicy() {
        return duplicatePolicy;
    }

    public void setDuplicatePolicy(Long duplicatePolicy) {
        this.duplicatePolicy = duplicatePolicy;
    }

    public Long getInterval() {
        return interval;
    }

    public void setInterval(Long interval) {
        this.interval = interval;
    }

    public Long getCodeType() {
        return codeType;
    }

    public void setCodeType(Long codeType) {
        this.codeType = codeType;
    }

    public boolean isReturnVerifyCode() {
        return returnVerifyCode;
    }

    public void setReturnVerifyCode(boolean returnVerifyCode) {
        this.returnVerifyCode = returnVerifyCode;
    }

    public String getSmsUpExtendCode() {
        return smsUpExtendCode;
    }

    public void setSmsUpExtendCode(String smsUpExtendCode) {
        this.smsUpExtendCode = smsUpExtendCode;
    }

    public boolean isSendSmsUpExtendCode() {
        return sendSmsUpExtendCode;
    }

    public void setSendSmsUpExtendCode(boolean sendSmsUpExtendCode) {
        this.sendSmsUpExtendCode = sendSmsUpExtendCode;
    }

    public String getOutId() {
        return outId;
    }

    public void setOutId(String outId) {
        this.outId = outId;
    }
}
