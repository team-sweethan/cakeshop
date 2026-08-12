package com.cakeshop.domain.order.entity;

import java.util.Set;

// 결제 완료는 PaymentStatus.DONE으로 관리하고 주문에는 업무 처리 상태만 저장한다.
public enum OrderStatus {

    PENDING_PAYMENT,   // 주문 생성 후 결제 대기
    UNDER_REVIEW,      // 수제 케이크 결제 완료 후 관리자 검토
    IN_PRODUCTION,     // 수제 케이크 제작 중
    READY_FOR_PICKUP,  // 일반 결제 완료 또는 수제 케이크 제작 완료 후 픽업 대기
    PICKED_UP,         // 픽업 완료 (최종)
    CANCELED,          // 고객 또는 관리자 취소 및 환불 완료 (최종)
    REJECTED,          // 수제 주문 반려 및 환불 완료 (최종)
    EXPIRED;           // 결제 기한 만료 (최종)

    // 주문 유형·취소 시각·권한 같은 추가 정책은 Service에서 검증한다.
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT ->
                Set.of(UNDER_REVIEW, READY_FOR_PICKUP, CANCELED, EXPIRED).contains(next);
            case UNDER_REVIEW ->
                Set.of(IN_PRODUCTION, REJECTED, CANCELED).contains(next);
            case IN_PRODUCTION -> next == READY_FOR_PICKUP;
            case READY_FOR_PICKUP ->
                Set.of(PICKED_UP, CANCELED).contains(next);
            case PICKED_UP, CANCELED, REJECTED, EXPIRED -> false;
        };
    }

    public boolean isFinal() {
        return this == REJECTED || this == PICKED_UP || this == CANCELED || this == EXPIRED;
    }
}
