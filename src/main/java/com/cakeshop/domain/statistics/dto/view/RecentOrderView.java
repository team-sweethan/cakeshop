package com.cakeshop.domain.statistics.dto.view;

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
        return switch (status) {
            case PENDING_PAYMENT -> "결제 대기";
            case UNDER_REVIEW -> "승인 대기";
            case READY_FOR_PICKUP -> "픽업 준비";
            case PICKED_UP -> "픽업 완료";
            case CANCELED -> "취소 완료";
            case REJECTED -> "주문 반려";
            case EXPIRED -> "결제 만료";
        };
    }

}
