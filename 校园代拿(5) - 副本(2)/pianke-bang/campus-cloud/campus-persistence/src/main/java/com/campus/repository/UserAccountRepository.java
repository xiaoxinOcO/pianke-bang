package com.campus.repository;

import com.campus.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    boolean existsByUsername(String username);
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    List<UserAccount> findByPhone(String phone);
    Optional<UserAccount> findByWxUnionId(String wxUnionId);
    Optional<UserAccount> findByWxMpOpenId(String wxMpOpenId);
    Optional<UserAccount> findByWxWebOpenId(String wxWebOpenId);
    List<UserAccount> findByUsernameIn(Collection<String> usernames);
    List<UserAccount> findByCreditScoreLessThan(Integer creditScore);

    List<UserAccount> findByCustomerServiceStaffTrueAndEnabledTrueOrderByCreatedAtAsc();

    List<UserAccount> findByCustomerServiceStaffTrueOrderByCreatedAtDesc();

    long countByPlatformAdminTrue();

    List<UserAccount> findByPlatformAdminTrueOrderByCreatedAtAsc();

    /**
     * 统计已占用该手机号的账号数（注册手机号或接单员申请手机号任一相同即计入，避免历史数据未写 phone 导致限制失效）。
     */
    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.phone = :mobile OR u.riderPhone = :mobile")
    long countByMobileBound(@Param("mobile") String mobile);
}
