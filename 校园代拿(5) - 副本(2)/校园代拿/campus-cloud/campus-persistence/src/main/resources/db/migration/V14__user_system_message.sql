CREATE TABLE IF NOT EXISTS user_system_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    title VARCHAR(80) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(40) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    ref_id BIGINT NULL,
    read_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_usm_username_created (username, created_at DESC),
    INDEX idx_usm_username_read (username, read_flag)
);
