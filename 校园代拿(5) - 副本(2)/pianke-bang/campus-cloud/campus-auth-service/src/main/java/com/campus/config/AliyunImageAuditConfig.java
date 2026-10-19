package com.campus.config;

import com.aliyun.teaopenapi.models.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunImageAuditConfig {

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "app.aliyun.image-audit", name = "enabled", havingValue = "true")
    public com.aliyun.imageaudit20191230.Client imageAuditClient(
            AliyunImageAuditProperties imageAuditProperties,
            AliyunDypnsProperties dypnsProperties
    ) throws Exception {
        String accessKeyId = firstNonBlank(
                System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"),
                imageAuditProperties.getAccessKeyId(),
                dypnsProperties.getAccessKeyId()
        );
        String accessKeySecret = firstNonBlank(
                System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
                imageAuditProperties.getAccessKeySecret(),
                dypnsProperties.getAccessKeySecret()
        );
        if (accessKeyId == null || accessKeySecret == null) {
            throw new IllegalStateException(
                    "图片审核已启用，但未配置 AccessKey。请设置 ALIBABA_CLOUD_ACCESS_KEY_ID/SECRET"
            );
        }

        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        String endpoint = imageAuditProperties.getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            config.endpoint = endpoint.trim();
        } else {
            config.endpoint = "imageaudit.cn-shanghai.aliyuncs.com";
        }
        return new com.aliyun.imageaudit20191230.Client(config);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
