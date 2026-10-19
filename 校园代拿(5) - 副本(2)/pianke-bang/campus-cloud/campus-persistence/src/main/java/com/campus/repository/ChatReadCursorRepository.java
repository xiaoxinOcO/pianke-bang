package com.campus.repository;

import com.campus.model.ChatReadCursor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatReadCursorRepository extends JpaRepository<ChatReadCursor, Long> {
    Optional<ChatReadCursor> findByReaderUsernameIgnoreCaseAndPeerUsernameIgnoreCase(
            String readerUsername,
            String peerUsername
    );
}
