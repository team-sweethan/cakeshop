package com.cakeshop.domain.order.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 수민
 * 담당자 : 주환
 * 작성일 : 2026-08-15
 * 기능 : 회원 마이페이지 주문 항목 응답
 * 설명 : 회원 도메인의 마이페이지에 제공하는 주문 한 건의 최소 조회 계약이다.
 * ******************************
 */
public record OrderMemberOrderView(
        long orderId,
        String orderNumber,
        String orderTypeLabel,
        String productName,
        int itemCount,
        String statusLabel,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime createdAt
) {
}
