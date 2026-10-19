package com.campus.client;

import com.campus.client.fallback.RealtimePushClientFallback;
import com.campus.config.ServiceConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = ServiceConstants.CHAT_SERVICE, contextId = "realtimePushClient", fallback = RealtimePushClientFallback.class)
public interface RealtimePushClient {

    @PostMapping("/internal/realtime/push")
    void push(@RequestBody Map<String, Object> body);
}
