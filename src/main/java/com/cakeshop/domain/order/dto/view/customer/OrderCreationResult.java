package com.cakeshop.domain.order.dto.view.customer;

import com.cakeshop.domain.order.entity.Order;
import java.time.LocalDateTime;

/** 주문 생성 결과와 기존 미결제 주문 안내 여부를 고객 웹 계층에 전달한다. */
public record OrderCreationResult(
        long orderId,
        PendingPaymentOrder pendingPaymentOrder
) {

    public static OrderCreationResult paymentReady(long orderId) {
        return new OrderCreationResult(orderId, null);
    }

    public static OrderCreationResult pendingPaymentGuide(Order order) {
        return new OrderCreationResult(
                order.getId(),
                new PendingPaymentOrder(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getPaymentExpiresAt()
                )
        );
    }

    public boolean requiresPendingPaymentGuide() {
        return pendingPaymentOrder != null;
    }

    /** 기존 결제 대기 주문 안내에 필요한 최소 정보다. */
    public record PendingPaymentOrder(
            long orderId,
            String orderNumber,
            LocalDateTime paymentExpiresAt
    ) {
    }
}
