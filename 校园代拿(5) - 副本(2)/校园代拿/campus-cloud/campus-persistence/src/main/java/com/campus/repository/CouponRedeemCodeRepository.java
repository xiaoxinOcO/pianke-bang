package com.campus.repository;

import com.campus.model.CouponRedeemCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CouponRedeemCodeRepository extends JpaRepository<CouponRedeemCode, Long> {

    Optional<CouponRedeemCode> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponRedeemCode c WHERE LOWER(c.code) = LOWER(:code)")
    Optional<CouponRedeemCode> findByCodeForUpdate(@Param("code") String code);

    List<CouponRedeemCode> findByTemplateIdOrderByCreatedAtDesc(Long templateId);

    List<CouponRedeemCode> findAllByOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCase(String code);

    void deleteByTemplateId(Long templateId);
}
