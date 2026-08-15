package com.cakeshop.domain.order.dto.view;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 수민
 * 담당자 : 주환
 * 작성일 : 2026-08-15
 * 기능 : 회원 마이페이지 주문 조회 행
 * 설명 : 마이페이지 주문 요약을 조립하기 위해 주문 도메인 안에서 사용하는 조회 행이다.
 * ******************************
 */
public record OrderMemberOrderRow(
        long orderId,
        String orderNumber,
        OrderType orderType,
        String productName,
        int itemCount,
        OrderStatus status,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime createdAt
) {
}
