package com.campus.dto;

import com.campus.model.UserSystemMessage;

import java.util.List;

public class SystemMessageListResponse {

    private List<UserSystemMessage> messages;
    private long unreadCount;

    public SystemMessageListResponse() {
    }

    public SystemMessageListResponse(List<UserSystemMessage> messages, long unreadCount) {
        this.messages = messages;
        this.unreadCount = unreadCount;
    }

    public List<UserSystemMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<UserSystemMessage> messages) {
        this.messages = messages;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
