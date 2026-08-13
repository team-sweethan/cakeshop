package com.cakeshop.domain.payment.event;

/** 일반 주문의 결제·주문·쿠폰·재고 처리 커밋 후 발행되는 도메인 이벤트다. */
public record GeneralPaymentCompletedEvent(long orderId) {
}
