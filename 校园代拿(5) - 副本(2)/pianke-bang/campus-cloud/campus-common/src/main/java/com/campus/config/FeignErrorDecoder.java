package com.campus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        String message = extractMessage(body);
        if (message == null || message.isBlank()) {
            message = defaultMessage(response.status(), methodKey);
        }

        if (response.status() == 400) {
            return new IllegalArgumentException(message);
        }
        if (response.status() == 404) {
            return new java.util.NoSuchElementException(message);
        }
        if (response.status() == 409) {
            return new IllegalStateException(message);
        }
        if (response.status() >= 500) {
            return new IllegalStateException(message);
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private static String defaultMessage(int status, String methodKey) {
        if (methodKey != null && methodKey.contains("WalletInternalClient")) {
            if (status == 503 || status == 502) {
                return "钱包服务暂不可用，请确认 campus-wallet-service 已启动并注册到 Nacos";
            }
            return "钱包服务调用失败(" + status + ")";
        }
        return "远程服务调用失败(" + status + ")";
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> data = objectMapper.readValue(body, Map.class);
            Object msg = data.get("message");
            if (msg != null && !String.valueOf(msg).isBlank()) {
                return String.valueOf(msg);
            }
            Object detail = data.get("detail");
            if (detail != null && !String.valueOf(detail).isBlank()) {
                return String.valueOf(detail);
            }
        } catch (Exception ignored) {
            if (body.length() < 500) {
                return body.trim();
            }
        }
        return null;
    }

    private static String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (InputStream in = response.body().asInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
