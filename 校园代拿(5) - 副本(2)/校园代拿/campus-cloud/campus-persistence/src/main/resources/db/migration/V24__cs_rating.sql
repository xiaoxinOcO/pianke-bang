CREATE TABLE IF NOT EXISTS cs_rating (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    from_username VARCHAR(50) NOT NULL,
    agent_username VARCHAR(50) NOT NULL,
    score INT NOT NULL,
    tags VARCHAR(255) NULL,
    comment_text VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cs_rating_agent_created (agent_username, created_at DESC),
    INDEX idx_cs_rating_created (created_at DESC),
    INDEX idx_cs_rating_score (score, created_at DESC)
);
