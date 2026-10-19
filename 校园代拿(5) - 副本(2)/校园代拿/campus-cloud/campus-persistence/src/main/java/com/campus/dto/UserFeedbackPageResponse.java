package com.campus.dto;

import java.util.List;

public class UserFeedbackPageResponse {

    private List<UserFeedbackDTO> records;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private long unreadCount;

    public UserFeedbackPageResponse(
            List<UserFeedbackDTO> records,
            int page,
            int size,
            long totalElements,
            int totalPages,
            long unreadCount
    ) {
        this.records = records;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.unreadCount = unreadCount;
    }

    public List<UserFeedbackDTO> getRecords() {
        return records;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getUnreadCount() {
        return unreadCount;
    }
}
