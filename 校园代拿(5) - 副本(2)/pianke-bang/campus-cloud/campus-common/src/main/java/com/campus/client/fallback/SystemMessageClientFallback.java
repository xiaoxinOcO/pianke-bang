package com.campus.client.fallback;

import com.campus.client.SystemMessageClient;
import com.campus.dto.SystemMessageCreateRequest;
import com.campus.model.UserSystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SystemMessageClientFallback implements SystemMessageClient {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageClientFallback.class);

    @Override
    public UserSystemMessage create(SystemMessageCreateRequest request) {
        log.warn("系统消息写入失败（audit-service 不可用）: eventType={}, username={}",
                request == null ? "" : request.getEventType(),
                request == null ? "" : request.getUsername());
        return null;
    }
}
