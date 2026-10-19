package com.campus.controller;

import com.campus.dto.ChatContactDTO;
import com.campus.dto.ChatReadMarkRequest;
import com.campus.dto.ChatSendRequest;
import com.campus.model.ChatMessage;
import com.campus.security.AuthContext;
import com.campus.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    public ChatMessage send(@RequestBody ChatSendRequest request) {
        return chatService.sendMessage(
                AuthContext.getCurrentUser(),
                AuthContext.getCurrentUserId(),
                request
        );
    }

    @GetMapping("/history")
    public List<ChatMessage> history(@RequestParam(value = "targetUser") String targetUser) {
        return chatService.history(
                AuthContext.getCurrentUser(),
                AuthContext.getCurrentUserId(),
                targetUser
        );
    }

    @GetMapping("/contacts")
    public List<ChatContactDTO> contacts() {
        return chatService.contacts(AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }

    @GetMapping("/temporary-contacts")
    public List<String> temporaryContacts() {
        return chatService.temporaryContacts(AuthContext.getCurrentUser(), AuthContext.getCurrentUserId());
    }

    /** 打开会话时上报已读 */
    @PostMapping("/read")
    public Map<String, Object> markRead(@RequestBody ChatReadMarkRequest request) {
        return chatService.markRead(
                AuthContext.getCurrentUser(),
                AuthContext.getCurrentUserId(),
                request
        );
    }

    /** 查询对方已读到哪条消息（用于显示「已读」） */
    @GetMapping("/peer-read")
    public Map<String, Object> peerRead(@RequestParam(value = "targetUser") String targetUser) {
        return chatService.peerReadState(
                AuthContext.getCurrentUser(),
                AuthContext.getCurrentUserId(),
                targetUser
        );
    }
}
