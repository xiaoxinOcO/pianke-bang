package com.campus.repository;

import com.campus.model.AlipayRechargeOrder;
import com.campus.model.AlipayRechargeStatus;
import com.campus.model.AlipayOrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlipayRechargeOrderRepository extends JpaRepository<AlipayRechargeOrder, Long> {

    Optional<AlipayRechargeOrder> findByOutTradeNo(String outTradeNo);

    Optional<AlipayRechargeOrder> findFirstByUsernameAndStatusOrderByCreatedAtDesc(
            String username,
            AlipayRechargeStatus status
    );

    Optional<AlipayRechargeOrder> findFirstByUsernameAndStatusAndOrderTypeOrderByCreatedAtDesc(
            String username,
            AlipayRechargeStatus status,
            AlipayOrderType orderType
    );

    Optional<AlipayRechargeOrder> findFirstByTaskIdAndOrderTypeOrderByCreatedAtDesc(
            Long taskId,
            AlipayOrderType orderType
    );

    Optional<AlipayRechargeOrder> findFirstByTaskIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
            Long taskId,
            AlipayOrderType orderType,
            AlipayRechargeStatus status
    );

    Optional<AlipayRechargeOrder> findFirstByTaskIdAndOrderTypeAndStatusOrderByPaidAtDesc(
            Long taskId,
            AlipayOrderType orderType,
            AlipayRechargeStatus status
    );
}
