package com.cakeshop.domain.order.dto.view;

/** 결제 관리자 화면이 주문 도메인에서 필요로 하는 최소 주문 정보다. */
public record OrderPaymentAdminView(
        long orderId,
        String orderNumber,
        String ordererName,
        String orderType
) {
}
