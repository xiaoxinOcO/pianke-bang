package com.campus.repository;

import com.campus.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByFromUsernameAndToUsernameOrFromUsernameAndToUsernameOrderByCreatedAtAsc(
            String fromA,
            String toA,
            String fromB,
            String toB
    );

    List<ChatMessage> findByFromUsernameOrToUsernameOrderByCreatedAtDesc(String fromUsername, String toUsername);

    @Query("""
            SELECT COALESCE(MAX(m.id), 0)
            FROM ChatMessage m
            WHERE (LOWER(m.fromUsername) = LOWER(:a) AND LOWER(m.toUsername) = LOWER(:b))
               OR (LOWER(m.fromUsername) = LOWER(:b) AND LOWER(m.toUsername) = LOWER(:a))
            """)
    Long findMaxIdBetween(@Param("a") String a, @Param("b") String b);
}
