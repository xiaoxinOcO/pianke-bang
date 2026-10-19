package com.campus.config;

import com.campus.security.InternalTokenSupport;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfiguration {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    @Bean
    public RequestInterceptor internalTokenFeignInterceptor(InternalTokenSupport internalTokenSupport) {
        return template -> {
            if (internalTokenSupport.isConfigured()) {
                template.header(ServiceConstants.HEADER_INTERNAL_TOKEN, internalTokenSupport.tokenValue());
            }
        };
    }
}
