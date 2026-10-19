package com.campus.client;

import com.campus.config.ServiceConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = ServiceConstants.AUTH_SERVICE, contextId = "authSessionClient")
public interface AuthSessionClient {

    @PostMapping("/internal/auth/validate-session")
    Map<String, Object> validateSession(@RequestBody Map<String, String> body);
}
