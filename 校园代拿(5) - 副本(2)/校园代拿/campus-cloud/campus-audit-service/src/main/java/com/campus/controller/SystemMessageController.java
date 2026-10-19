package com.campus.controller;

import com.campus.dto.SystemMessageCreateRequest;
import com.campus.dto.SystemMessageListResponse;
import com.campus.model.UserSystemMessage;
import com.campus.security.AdminPolicy;
import com.campus.security.AuthContext;
import com.campus.service.UserSystemMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-messages")
public class SystemMessageController {

    private final UserSystemMessageService userSystemMessageService;
    private final AdminPolicy adminPolicy;

    public SystemMessageController(
            UserSystemMessageService userSystemMessageService,
            AdminPolicy adminPolicy
    ) {
        this.userSystemMessageService = userSystemMessageService;
        this.adminPolicy = adminPolicy;
    }

    @GetMapping
    public SystemMessageListResponse listMine() {
        return userSystemMessageService.listForUser(AuthContext.getCurrentUser());
    }

    @PostMapping("/read")
    public void markAllRead() {
        userSystemMessageService.markAllRead(AuthContext.getCurrentUser());
    }

    /** 管理员向指定用户发送系统通知（如申诉补充说明） */
    @PostMapping("/admin/send")
    public UserSystemMessage adminSend(@RequestBody SystemMessageCreateRequest request) {
        String operator = AuthContext.getCurrentUser();
        if (operator == null || !adminPolicy.isAdmin(operator)) {
            throw new IllegalStateException("仅管理员可发送系统通知");
        }
        UserSystemMessage message = userSystemMessageService.create(request);
        if (message == null) {
            throw new IllegalStateException("系统消息已存在，未重复发送");
        }
        return message;
    }
}
