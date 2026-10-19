CREATE TABLE IF NOT EXISTS chat_read_cursor (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reader_username VARCHAR(50) NOT NULL,
    peer_username VARCHAR(50) NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chat_read_reader_peer (reader_username, peer_username),
    INDEX idx_chat_read_peer (peer_username, reader_username)
);
