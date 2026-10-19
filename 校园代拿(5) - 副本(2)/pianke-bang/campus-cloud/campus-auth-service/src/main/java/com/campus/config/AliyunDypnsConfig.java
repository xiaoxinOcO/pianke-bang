package com.campus.config;



import com.aliyun.auth.credentials.Credential;

import com.aliyun.auth.credentials.provider.ICredentialProvider;

import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;

import com.aliyun.auth.credentials.provider.StaticCredentialProvider;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;

import darabonba.core.client.ClientOverrideConfiguration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;



@Configuration

public class AliyunDypnsConfig {



    @Bean(destroyMethod = "close")

    @ConditionalOnProperty(prefix = "app.aliyun.dypns", name = "enabled", havingValue = "true")

    public AsyncClient dypnsAsyncClient(AliyunDypnsProperties properties) {

        ICredentialProvider credentials = resolveCredentialProvider(properties);

        var builder = AsyncClient.builder()

                .region(properties.getRegion())

                .credentialsProvider(credentials);

        String ep = properties.getEndpoint();

        if (ep != null && !ep.isBlank()) {

            builder.overrideConfiguration(

                    ClientOverrideConfiguration.create().setEndpointOverride(ep.trim())

            );

        }

        return builder.build();

    }



    static ICredentialProvider resolveCredentialProvider(AliyunDypnsProperties properties) {

        String envId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");

        String envSecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");

        if (isNonBlank(envId) && isNonBlank(envSecret)) {

            return EnvironmentVariableCredentialProvider.create();

        }

        String propId = properties.getAccessKeyId();

        String propSecret = properties.getAccessKeySecret();

        if (isNonBlank(propId) && isNonBlank(propSecret)) {

            return StaticCredentialProvider.create(

                    Credential.builder()

                            .accessKeyId(propId.trim())

                            .accessKeySecret(propSecret.trim())

                            .build()

            );

        }

        return EnvironmentVariableCredentialProvider.create();

    }



    private static boolean isNonBlank(String value) {

        return value != null && !value.isBlank();

    }

}


