package com.campus.controller.internal;

import com.campus.dto.SystemMessageCreateRequest;
import com.campus.model.UserSystemMessage;
import com.campus.service.UserSystemMessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/system-messages")
public class SystemMessageInternalController {

    private final UserSystemMessageService userSystemMessageService;

    public SystemMessageInternalController(UserSystemMessageService userSystemMessageService) {
        this.userSystemMessageService = userSystemMessageService;
    }

    @PostMapping("/create")
    public UserSystemMessage create(@RequestBody SystemMessageCreateRequest request) {
        return userSystemMessageService.create(request);
    }
}
