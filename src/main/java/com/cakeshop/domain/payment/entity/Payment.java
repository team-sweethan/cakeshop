package com.cakeshop.domain.payment.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DB의 payments 한 행을 표현한다. */
@Getter
@Setter
public class Payment {

    private Long id;
    private Long orderId;
    private String tossOrderId;
    private String paymentKey;
    private String idempotencyKey;
    private String method;
    private BigDecimal amount;
    private PaymentStatus status;
    private String providerStatus;
    private Long activePaidOrderId;
    private Long activeReadyOrderId;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
