package com.campus.controller.internal;

import com.campus.service.RealtimePushService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/realtime")
public class RealtimeInternalController {

    private final RealtimePushService realtimePushService;

    public RealtimeInternalController(RealtimePushService realtimePushService) {
        this.realtimePushService = realtimePushService;
    }

    @PostMapping("/push")
    public void push(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", ""));
        String type = String.valueOf(body.getOrDefault("type", ""));
        Object data = body.get("data");
        realtimePushService.sendToUser(username, type, data);
    }
}
