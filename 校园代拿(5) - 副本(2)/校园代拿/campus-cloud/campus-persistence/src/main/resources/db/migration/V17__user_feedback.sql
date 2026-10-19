CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    page_name VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    images_json LONGTEXT NULL,
    read_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_feedback_created (created_at DESC),
    INDEX idx_user_feedback_read (read_flag, created_at DESC),
    INDEX idx_user_feedback_type (feedback_type, created_at DESC)
);
