package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ChatSendRequest {
    @JsonAlias({"targetUser"})
    private String toUsername;
    private String content;

    public String getToUsername() {
        return toUsername;
    }

    public void setToUsername(String toUsername) {
        this.toUsername = toUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
