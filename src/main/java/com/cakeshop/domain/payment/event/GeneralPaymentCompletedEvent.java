package com.cakeshop.domain.payment.event;

/** 일반 주문의 결제·주문·쿠폰·재고 처리를 마친 트랜잭션 안에서 발행하는 도메인 이벤트다. */
public record GeneralPaymentCompletedEvent(long orderId) {
}
