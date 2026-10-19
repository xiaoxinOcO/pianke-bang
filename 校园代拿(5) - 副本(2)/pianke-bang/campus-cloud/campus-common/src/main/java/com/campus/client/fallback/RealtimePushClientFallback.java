package com.campus.client.fallback;

import com.campus.client.RealtimePushClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 实时推送熔断降级：推送失败不影响任务主流程。
 */
@Component
public class RealtimePushClientFallback implements RealtimePushClient {

    private static final Logger log = LoggerFactory.getLogger(RealtimePushClientFallback.class);

    @Override
    public void push(Map<String, Object> body) {
        log.warn("实时推送服务不可用，已跳过: username={}, type={}",
                body.get("username"), body.get("type"));
    }
}
