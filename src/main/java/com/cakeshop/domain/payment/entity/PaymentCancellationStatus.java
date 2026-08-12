package com.cakeshop.domain.payment.entity;

/** 결제 취소 요청의 처리 상태인 payment_cancellations.status 값이다. */
public enum PaymentCancellationStatus {
    REQUESTED,      // 취소 요청됨
    DONE,           // PG 취소 및 내부 반영 완료
    FAILED          // 취소 처리 실패.
}
