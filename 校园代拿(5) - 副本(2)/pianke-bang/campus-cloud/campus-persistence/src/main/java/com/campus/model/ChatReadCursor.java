package com.campus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_read_cursor",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_read_reader_peer",
                columnNames = {"reader_username", "peer_username"}
        )
)
public class ChatReadCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_username", nullable = false, length = 50)
    private String readerUsername;

    @Column(name = "peer_username", nullable = false, length = 50)
    private String peerUsername;

    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getReaderUsername() {
        return readerUsername;
    }

    public void setReaderUsername(String readerUsername) {
        this.readerUsername = readerUsername;
    }

    public String getPeerUsername() {
        return peerUsername;
    }

    public void setPeerUsername(String peerUsername) {
        this.peerUsername = peerUsername;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId == null ? 0L : lastReadMessageId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
