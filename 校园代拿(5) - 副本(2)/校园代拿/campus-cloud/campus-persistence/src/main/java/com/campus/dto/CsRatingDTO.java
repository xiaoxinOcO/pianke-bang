package com.campus.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CsRatingDTO {
    private Long id;
    private String fromUsername;
    private String agentUsername;
    private Integer score;
    private List<String> tags;
    private String comment;
    private LocalDateTime createdAt;

    public CsRatingDTO(
            Long id,
            String fromUsername,
            String agentUsername,
            Integer score,
            List<String> tags,
            String comment,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.fromUsername = fromUsername;
        this.agentUsername = agentUsername;
        this.score = score;
        this.tags = tags;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public String getAgentUsername() {
        return agentUsername;
    }

    public Integer getScore() {
        return score;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
