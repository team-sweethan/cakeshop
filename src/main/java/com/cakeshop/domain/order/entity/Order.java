package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DB의 orders 한 행을 표현한다. */
@Getter
@Setter
public class Order {

    private Long id;                // 내부 주문 식별자
    private String orderNumber;     // 고객에게 노출하는 주문번호
    // member.id
    private Long memberId;          // "주문 회원" 식별자
    private String requestKey;      // 회원별 주문 생성 [멱등키]
    private OrderType orderType;    // 주문 유형: GENERAL 또는 CUSTOM
    private String ordererName;     // 주문 당시 주문자 이름 스냅샷
    private String ordererPhone;    // 주문 당시 주문자 연락처 스냅샷
    private String pickupName;      // 픽업자 이름
    private String pickupPhone;     // 픽업자 연락처
    private BigDecimal originalAmount; // 할인 전 주문 금액
    private BigDecimal discountAmount; // 할인 금액
    private BigDecimal finalAmount; // 최종 결제 금액
    private OrderStatus status;     // 주문 업무 상태
    private LocalDateTime pickupAt; // 픽업 예정 시각
    private LocalDateTime paymentExpiresAt; // 결제 만료 시각
    private String requestMessage;          // 주문 요청 사항
    private String rejectReason;            // 주문제작 반려 사유
    private LocalDateTime underReviewAt;    // 주문제작 검토 시작 시각
    private LocalDateTime approvedAt;       // 주문제작 제작 시작 승인 시각
    private LocalDateTime rejectedAt;       // 주문제작 반려 처리 시각
    //member.id
    private Long rejectedBy;                // "반려 처리 관리자 회원" 식별자
    private LocalDateTime readyAt;          // 픽업 준비 완료 상태로 전환된 시각
    //member.id
    private Long approvedBy;                // 주문제작 "승인 처리 관리자" 회원 식별자
    private LocalDateTime pickedUpAt;       // 픽업 완료 처리 시각
    //member.id
    private Long pickedUpBy;                // "픽업 완료 처리 관리자" 회원 식별자
    private LocalDateTime expiredAt;        // 결제 기한 만료 처리 시각
    private LocalDateTime canceledAt;       // 주문 취소 처리 시각
    private String cancelReason;            // 주문 취소 사유
    private String canceledBy;              // 주문 취소 주체 구분값
    private LocalDateTime pickupReminderSentAt; // 픽업 알림 발송 시각
    private LocalDateTime createdAt;        // 생성 시각
    private LocalDateTime updatedAt;        // 최종 수정 시각
}
