package com.cakeshop.domain.order.dto.view;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 주문 목록에 필요한 주문·첫 상품 스냅샷이다. */
public record OrderListView(
        long orderId,
        String orderNumber,
        String ordererName,
        OrderType orderType,
        OrderStatus status,
        String productName,
        int itemCount,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime createdAt
) {
    public String orderTypeLabel() {
        return orderType.orderTypeLabel();
    }

    public String statusLabel() {
        return status.statusLabel();
    }
}
