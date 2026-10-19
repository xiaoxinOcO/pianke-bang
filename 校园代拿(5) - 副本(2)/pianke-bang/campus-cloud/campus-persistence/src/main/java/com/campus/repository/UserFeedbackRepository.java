package com.campus.repository;

import com.campus.model.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {

    @Query("""
            SELECT f FROM UserFeedback f
            WHERE (:type IS NULL OR f.feedbackType = :type)
              AND (:readFlag IS NULL OR f.readFlag = :readFlag)
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    Page<UserFeedback> search(
            @Param("type") String type,
            @Param("readFlag") Boolean readFlag,
            Pageable pageable
    );

    long countByReadFlagFalse();
}
