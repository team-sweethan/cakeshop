package com.cakeshop.domain.order.entity;

import java.util.Set;

// 일반·수제 주문 공통 상태 7개 — 주문 종류별 전이는 Service에서 구분한다.
public enum OrderStatus {

    /*
    결제 성공 후
    일반 상품 -> READY_FOR_PICKUP
    수제 상품 -> UNDER_REVIEW
     */
    PENDING_PAYMENT,   // 결제 대기
    UNDER_REVIEW,      // 수제 케이크 확인 중
    READY_FOR_PICKUP,  // 픽업 대기
    PICKED_UP,         // 픽업 완료
    CANCELED,          // 주문 취소
    REJECTED,          // 수제 케이크 반려
    EXPIRED;           // 결제 시간 만료

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT ->
                    Set.of(UNDER_REVIEW, READY_FOR_PICKUP, CANCELED, EXPIRED)
                            .contains(next);

            case UNDER_REVIEW ->
                    Set.of(READY_FOR_PICKUP, REJECTED, CANCELED)
                            .contains(next);

            case READY_FOR_PICKUP ->
                    Set.of(PICKED_UP, CANCELED)
                            .contains(next);

            case PICKED_UP, CANCELED, REJECTED, EXPIRED -> false;
        };
    }

    public boolean isFinal() {
        return this == PICKED_UP
                || this == CANCELED
                || this == REJECTED
                || this == EXPIRED;
    }
}
