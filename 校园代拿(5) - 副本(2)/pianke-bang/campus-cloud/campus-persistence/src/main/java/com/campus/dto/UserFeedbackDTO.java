package com.campus.dto;

import java.time.LocalDateTime;
import java.util.List;

public class UserFeedbackDTO {

    private Long id;
    private String username;
    private String feedbackType;
    private String pageName;
    private String content;
    private List<String> images;
    private boolean readFlag;
    private LocalDateTime createdAt;

    public UserFeedbackDTO(
            Long id,
            String username,
            String feedbackType,
            String pageName,
            String content,
            List<String> images,
            boolean readFlag,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.username = username;
        this.feedbackType = feedbackType;
        this.pageName = pageName;
        this.content = content;
        this.images = images;
        this.readFlag = readFlag;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public String getPageName() {
        return pageName;
    }

    public String getContent() {
        return content;
    }

    public List<String> getImages() {
        return images;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
