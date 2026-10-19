package com.campus.repository;

import com.campus.model.CsRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CsRatingRepository extends JpaRepository<CsRating, Long> {

    @Query("""
            SELECT r FROM CsRating r
            WHERE (:agent IS NULL OR r.agentUsername = :agent)
              AND (:scoreMin IS NULL OR r.score >= :scoreMin)
              AND (:scoreMax IS NULL OR r.score <= :scoreMax)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<CsRating> search(
            @Param("agent") String agent,
            @Param("scoreMin") Integer scoreMin,
            @Param("scoreMax") Integer scoreMax,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(AVG(r.score), 0) FROM CsRating r
            WHERE (:agent IS NULL OR r.agentUsername = :agent)
              AND (:scoreMin IS NULL OR r.score >= :scoreMin)
              AND (:scoreMax IS NULL OR r.score <= :scoreMax)
            """)
    Double averageScore(
            @Param("agent") String agent,
            @Param("scoreMin") Integer scoreMin,
            @Param("scoreMax") Integer scoreMax
    );

    long countByAgentUsername(String agentUsername);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM CsRating r WHERE r.agentUsername = :agent")
    Double averageScoreByAgent(@Param("agent") String agent);
}
