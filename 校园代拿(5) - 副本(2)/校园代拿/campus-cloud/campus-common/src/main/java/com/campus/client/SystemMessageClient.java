package com.campus.client;

import com.campus.client.fallback.SystemMessageClientFallback;
import com.campus.config.ServiceConstants;
import com.campus.dto.SystemMessageCreateRequest;
import com.campus.model.UserSystemMessage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ServiceConstants.AUDIT_SERVICE, contextId = "systemMessageClient", fallback = SystemMessageClientFallback.class)
public interface SystemMessageClient {

    @PostMapping("/internal/system-messages/create")
    UserSystemMessage create(@RequestBody SystemMessageCreateRequest request);
}
