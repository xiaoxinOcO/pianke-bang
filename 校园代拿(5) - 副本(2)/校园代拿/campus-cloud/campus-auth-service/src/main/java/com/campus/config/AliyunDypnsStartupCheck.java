package com.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时检查号码认证所需凭证，避免 IDE 未注入环境变量时排查困难。
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "app.aliyun.dypns", name = "enabled", havingValue = "true")
public class AliyunDypnsStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AliyunDypnsStartupCheck.class);

    private final AliyunDypnsProperties dypnsProperties;

    public AliyunDypnsStartupCheck(AliyunDypnsProperties dypnsProperties) {
        this.dypnsProperties = dypnsProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String envId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        String envSecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        boolean fromEnv = isNonBlank(envId) && isNonBlank(envSecret);

        String propId = dypnsProperties.getAccessKeyId();
        String propSecret = dypnsProperties.getAccessKeySecret();
        boolean fromProps = isNonBlank(propId) && isNonBlank(propSecret);

        if (!fromEnv && !fromProps) {
            log.warn(
                    "【阿里云短信】未检测到凭证：请设置环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET，"
                            + "或复制 campus-common/.../application-secret.yml.example 为 application-secret.yml（勿提交）。"
            );
            return;
        }

        String safeId = fromEnv ? envId.trim() : propId.trim();
        String masked = safeId.length() <= 10 ? "****" : safeId.substring(0, 6) + "…" + safeId.substring(safeId.length() - 4);
        log.info(
                "【阿里云短信】AccessKey 已加载（来源: {}，Id 掩码: {}），region 以 app.aliyun.dypns.region 为准",
                fromEnv ? "环境变量" : "配置文件",
                masked
        );

        String region = dypnsProperties.getRegion() == null ? "" : dypnsProperties.getRegion().trim();
        String scheme = dypnsProperties.getSchemeName() == null ? "" : dypnsProperties.getSchemeName().trim();
        String sign = dypnsProperties.getSignName() == null ? "" : dypnsProperties.getSignName().trim();
        String tpl = dypnsProperties.getTemplateCode() == null ? "" : dypnsProperties.getTemplateCode().trim();
        log.info(
                "【阿里云短信】生效配置 region={} schemeName=[{}] signName=[{}] templateCode={}",
                region.isEmpty() ? "(未设)" : region,
                scheme,
                sign,
                tpl.isEmpty() ? "(未设)" : tpl
        );

        String extend = dypnsProperties.getSmsUpExtendCode() == null ? "" : dypnsProperties.getSmsUpExtendCode().trim();
        String envExtendVal = System.getenv("APP_ALIYUN_DYNS_SMS_UP_EXTEND_CODE");
        boolean envExtend = envExtendVal != null && !envExtendVal.isBlank();
        if (!extend.isEmpty() && !dypnsProperties.isSendSmsUpExtendCode()) {
            log.warn(
                    "【阿里云短信】检测到 SmsUpExtendCode 非空（可能来自环境变量 APP_ALIYUN_DYNS_SMS_UP_EXTEND_CODE={}），"
                            + "但 app.aliyun.dypns.send-sms-up-extend-code=false，请求中不会携带扩展码。若仍报「签名或模板无效」，请核对控制台 SchemeName/SignName/TemplateCode 与 region。",
                    envExtend
            );
        } else if (dypnsProperties.isSendSmsUpExtendCode() && !extend.isEmpty()) {
            log.info("【阿里云短信】将携带 SmsUpExtendCode 发送（长度 {}）", extend.length());
        }
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
