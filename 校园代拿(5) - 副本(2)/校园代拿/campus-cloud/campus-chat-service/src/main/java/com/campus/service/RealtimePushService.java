package com.campus.service;

import com.campus.dto.RealtimeEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimePushService {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 推送给指定用户（用户名作为 Principal name）。
     * 订阅地址：/user/queue/events
     */
    public void sendToUser(String username, String type, Object data) {
        if (username == null || username.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(username.trim(), "/queue/events", new RealtimeEvent(type, data));
    }
}

