package com.campus.mq;

public class TaskStatusMessage {

    private Long taskId;
    private String status;
    private String title;
    private String event;
    private String publisherName;
    private String claimerName;

    public TaskStatusMessage() {
    }

    public TaskStatusMessage(Long taskId, String status, String title, String event,
                             String publisherName, String claimerName) {
        this.taskId = taskId;
        this.status = status;
        this.title = title;
        this.event = event;
        this.publisherName = publisherName;
        this.claimerName = claimerName;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getClaimerName() {
        return claimerName;
    }

    public void setClaimerName(String claimerName) {
        this.claimerName = claimerName;
    }
}
