package com.campus.controller;

import com.campus.dto.UserFeedbackDTO;
import com.campus.dto.UserFeedbackSubmitRequest;
import com.campus.security.AuthContext;
import com.campus.service.UserFeedbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final UserFeedbackService userFeedbackService;

    public FeedbackController(UserFeedbackService userFeedbackService) {
        this.userFeedbackService = userFeedbackService;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody UserFeedbackSubmitRequest request) {
        UserFeedbackDTO saved = userFeedbackService.submit(AuthContext.getCurrentUser(), request);
        return Map.of(
                "message", "反馈已提交，感谢你的建议！",
                "id", saved.getId()
        );
    }
}
