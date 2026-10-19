package com.campus.support;

import com.campus.client.RealtimePushClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "campus.remote.realtime", havingValue = "true")
public class RemoteRealtimePushSupport {

    private static final Logger log = LoggerFactory.getLogger(RemoteRealtimePushSupport.class);

    private final RealtimePushClient realtimePushClient;

    public RemoteRealtimePushSupport(RealtimePushClient realtimePushClient) {
        this.realtimePushClient = realtimePushClient;
    }

    public void sendToUser(String username, String type, Object data) {
        try {
            realtimePushClient.push(Map.of(
                    "username", username == null ? "" : username,
                    "type", type == null ? "" : type,
                    "data", data == null ? Map.of() : data
            ));
        } catch (Exception ex) {
            log.warn("实时推送失败，已跳过: username={}, type={}, reason={}",
                    username, type, ex.getMessage());
        }
    }
}
