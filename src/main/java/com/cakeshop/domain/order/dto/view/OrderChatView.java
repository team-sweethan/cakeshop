package com.cakeshop.domain.order.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 채팅 및 알림용 주문 정보 조회 계약 DTO
 * 설명 : 채팅 및 알림 도메인이 orders 테이블을 직접 JOIN하거나 Order Entity를 참조하지 않도록 연동에 필요한 최소 필드만 제공한다.
 * ******************************
 */
public record OrderChatView(
        Long id,
        String orderNumber,
        Long memberId,
        String orderType,
        String status,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime orderCreatedAt,
        LocalDateTime orderUpdatedAt,
        String representativeProductName,
        String rejectReason
) {
}
