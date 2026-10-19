package com.campus.support;

import com.campus.model.AlipayOrderType;
import com.campus.model.AlipayRechargeOrder;
import com.campus.model.AlipayRechargeStatus;
import com.campus.repository.AlipayRechargeOrderRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 判断任务赏金是否经支付宝沙箱支付，以便退款时走 wallet-service 的原路退回逻辑。
 */
@Component
public class AlipayBountyOrderSupport {

    private final AlipayRechargeOrderRepository orderRepository;

    public AlipayBountyOrderSupport(AlipayRechargeOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public boolean shouldRefundViaAlipay(String publisherName, Long taskId) {
        if (taskId == null || publisherName == null || publisherName.isBlank()) {
            return false;
        }
        String user = publisherName.trim().toLowerCase();

        Optional<AlipayRechargeOrder> paidOrder = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByPaidAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PAID)
                .or(() -> orderRepository.findFirstByTaskIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.PAID));
        if (paidOrder.isPresent() && user.equalsIgnoreCase(paidOrder.get().getUsername())) {
            return true;
        }

        Optional<AlipayRechargeOrder> refundedOrder = orderRepository
                .findFirstByTaskIdAndOrderTypeAndStatusOrderByPaidAtDesc(
                        taskId, AlipayOrderType.TASK_BOUNTY, AlipayRechargeStatus.REFUNDED);
        return refundedOrder.isPresent() && user.equalsIgnoreCase(refundedOrder.get().getUsername());
    }

    /** 是否存在未退款的支付宝赏金订单（含 PENDING：沙箱已付但回调/同步未到） */
    public boolean hasUnsettledAlipayBountyOrder(String publisherName, Long taskId) {
        if (taskId == null || publisherName == null || publisherName.isBlank()) {
            return false;
        }
        String user = publisherName.trim().toLowerCase();
        return orderRepository.findFirstByTaskIdAndOrderTypeOrderByCreatedAtDesc(taskId, AlipayOrderType.TASK_BOUNTY)
                .filter(order -> user.equalsIgnoreCase(order.getUsername()))
                .map(order -> order.getStatus() == AlipayRechargeStatus.PENDING
                        || order.getStatus() == AlipayRechargeStatus.PAID)
                .orElse(false);
    }
}
