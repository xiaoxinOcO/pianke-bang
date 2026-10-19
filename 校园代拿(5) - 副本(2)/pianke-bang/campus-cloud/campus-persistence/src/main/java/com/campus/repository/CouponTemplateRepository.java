package com.campus.repository;

import com.campus.model.CouponClaimMode;
import com.campus.model.CouponTemplate;
import com.campus.model.CouponTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {
    List<CouponTemplate> findByStatusOrderByCreatedAtDesc(CouponTemplateStatus status);

    List<CouponTemplate> findByClaimModeAndStatusOrderByIdAsc(CouponClaimMode claimMode, CouponTemplateStatus status);

    List<CouponTemplate> findBySceneCodeAndStatusOrderByIdAsc(String sceneCode, CouponTemplateStatus status);

    boolean existsByNameAndClaimMode(String name, CouponClaimMode claimMode);
}
