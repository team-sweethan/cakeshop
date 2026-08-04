package com.cakeshop.domain.payment.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DB의 payment_cancellations 한 행을 표현한다. */
@Getter
@Setter
public class PaymentCancellation {

    private Long id;
    private Long paymentId;
    private String idempotencyKey;
    private BigDecimal cancelAmount;
    private String cancelReason;
    private String requestType;
    private Long requestedBy;
    private PaymentCancellationStatus status;
    private Long activeRequestedPaymentId;
    private String transactionKey;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
