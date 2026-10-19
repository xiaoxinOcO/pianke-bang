package com.campus.repository;

import com.campus.model.SmsVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, Long> {

    Optional<SmsVerificationCode> findTopByPhoneOrderByIdDesc(String phone);

    @Query("""
            SELECT s FROM SmsVerificationCode s
            WHERE s.phone = :phone AND s.code = :code AND s.consumed = false AND s.expireAt > :now
            ORDER BY s.id DESC
            """)
    Optional<SmsVerificationCode> findFirstValid(
            @Param("phone") String phone,
            @Param("code") String code,
            @Param("now") LocalDateTime now
    );
}
