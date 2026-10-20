package com.campus.dto;

public class NoticeUpsertRequest {
    private String title;
    private String content;
    private boolean published;
    private boolean pinned;
    private boolean linkEnabled;
    private String linkPath;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isLinkEnabled() {
        return linkEnabled;
    }

    public void setLinkEnabled(boolean linkEnabled) {
        this.linkEnabled = linkEnabled;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public void setLinkPath(String linkPath) {
        this.linkPath = linkPath;
    }
}

