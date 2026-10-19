package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ChatReadMarkRequest {

    @JsonAlias({"targetUser", "peerUsername", "withUser"})
    private String targetUser;

    private Long lastReadMessageId;

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }
}
