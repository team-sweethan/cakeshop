package com.cakeshop.domain.dashboard.dto.view;

import com.cakeshop.domain.order.entity.OrderStatus;
import java.math.BigDecimal;

/** 관리자 대시보드의 최근 주문 한 건을 전달한다. */
public record RecentOrderView(
        long orderId,
        String orderNumber,
        String productName,
        OrderStatus status,
        BigDecimal finalAmount
) {

    public String statusLabel() {
        return status.statusLabel();
    }

}
