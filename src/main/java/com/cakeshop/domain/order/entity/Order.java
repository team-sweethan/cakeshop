package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DB의 orders 한 행을 표현한다. */
@Getter
@Setter
public class Order {

    private Long id;
    private String orderNumber;
    private Long memberId;
    private OrderType orderType;
    private String ordererName;
    private String ordererPhone;
    private String pickupName;
    private String pickupPhone;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private OrderStatus status;
    private LocalDateTime pickupAt;
    private LocalDateTime cancellationBlockedAt;
    private LocalDateTime paymentExpiresAt;
    private String requestMessage;
    private String rejectReason;
    private LocalDateTime underReviewAt;
    private LocalDateTime rejectedAt;
    private Long rejectedBy;
    private LocalDateTime readyAt;
    private Long approvedBy;
    private LocalDateTime pickedUpAt;
    private Long pickedUpBy;
    private LocalDateTime expiredAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private String canceledBy;
    private LocalDateTime pickupReminderSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
