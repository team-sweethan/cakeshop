package com.cakeshop.domain.payment.entity;

// 토스 결제 상태 — OrderStatus와 분리, 주문 enum에 섞지 않는다
public enum PaymentStatus {
    READY,             // 결제 전
    DONE,              // 결제 완료
    CANCELED,          // 전액 취소
    PARTIAL_CANCELED,  // 부분 취소
    ABORTED,           // 결제 중단/실패
    EXPIRED            // 결제 만료
}
