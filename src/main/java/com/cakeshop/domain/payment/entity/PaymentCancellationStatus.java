package com.cakeshop.domain.payment.entity;

/** 결제 취소 요청의 처리 상태인 payment_cancellations.status 값이다. */
public enum PaymentCancellationStatus {
    REQUESTED,
    DONE,
    FAILED
}
