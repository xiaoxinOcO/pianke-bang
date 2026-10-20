package com.campus.repository;

import com.campus.model.UserCoupon;
import com.campus.model.UserCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUsernameIgnoreCaseAndStatusOrderByObtainedAtDesc(String username, UserCouponStatus status);

    List<UserCoupon> findByUsernameIgnoreCaseOrderByObtainedAtDesc(String username);

    long countByTemplateIdAndUsernameIgnoreCase(Long templateId, String username);

    long countByTemplateId(Long templateId);

    List<UserCoupon> findByTemplateId(Long templateId);

    Optional<UserCoupon> findByUsedTaskId(Long usedTaskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UserCoupon c WHERE c.id = :id")
    Optional<UserCoupon> findByIdForUpdate(@Param("id") Long id);
}
