package com.campus.dto;

/**
 * WebSocket 推送给前端的统一事件格式
 */
public class RealtimeEvent {
    private String type;
    private Object data;

    public RealtimeEvent() {
    }

    public RealtimeEvent(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

