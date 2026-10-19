package com.campus.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerSupportChatRequest {
    private String message;
    private List<CustomerSupportChatMessage> history = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<CustomerSupportChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<CustomerSupportChatMessage> history) {
        this.history = history;
    }
}
