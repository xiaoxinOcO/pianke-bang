package com.campus.repository;

import com.campus.model.HallActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HallActivityRepository extends JpaRepository<HallActivity, Long> {

    List<HallActivity> findByStatusOrderBySortOrderAscIdDesc(String status);

    List<HallActivity> findAllByOrderBySortOrderAscIdDesc();

    List<HallActivity> findByCouponTemplateId(Long couponTemplateId);

    boolean existsByTitleAndActionType(String title, String actionType);
}
