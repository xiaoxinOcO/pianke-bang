package com.campus.repository;

import com.campus.model.CouponRedeemLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CouponRedeemLogRepository extends JpaRepository<CouponRedeemLog, Long> {
    boolean existsByCodeIdAndUsernameIgnoreCase(Long codeId, String username);

    void deleteByCodeIdIn(Collection<Long> codeIds);
}
