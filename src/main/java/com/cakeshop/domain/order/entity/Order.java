package com.cakeshop.domain.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** DB의 orders 한 행을 표현한다. 화면 입력값과 출력값은 DTO로 분리한다. */
@Getter
@Setter
public class Order {

    private Long id;
    private String orderNumber;
    private Long memberId;
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
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime expiredAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private String canceledBy;
    private LocalDateTime pickupReminderSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
