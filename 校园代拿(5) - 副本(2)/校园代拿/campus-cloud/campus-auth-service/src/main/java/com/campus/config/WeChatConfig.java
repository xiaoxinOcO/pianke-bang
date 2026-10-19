package com.campus.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

@Configuration
@EnableConfigurationProperties(WeChatProperties.class)
public class WeChatConfig {

    @Bean
    @Qualifier("wechatRestTemplate")
    public RestTemplate wechatRestTemplate() {
        // 微信部分接口对 Expect: 100-continue 会直接回 412，需去掉该头
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setRequestProperty("Expect", "");
            }
        };
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }
}
