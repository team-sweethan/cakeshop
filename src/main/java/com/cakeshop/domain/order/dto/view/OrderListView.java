package com.cakeshop.domain.order.dto.view;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 주문 목록에 필요한 주문·첫 상품 스냅샷이다. */
public record OrderListView(
        long orderId,
        String orderNumber,
        long memberId,
        String ordererName,
        OrderType orderType,
        OrderStatus status,
        String productName,
        int itemCount,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime createdAt
) {
    public String statusLabel() {
        return switch (status) {
            case PENDING_PAYMENT -> "결제 대기";
            case UNDER_REVIEW -> "승인 대기";
            case IN_PRODUCTION -> "제작 중";
            case READY_FOR_PICKUP -> "픽업 준비";
            case PICKED_UP -> "픽업 완료";
            case CANCELED -> "취소 완료";
            case REJECTED -> "주문 반려";
            case EXPIRED -> "결제 만료";
        };
    }
}
