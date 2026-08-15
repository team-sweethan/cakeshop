package com.cakeshop.domain.order.dto.view;

import java.util.List;

/**
 * ******************************
 * 작성자 : 수민
 * 담당자 : 주환
 * 작성일 : 2026-08-15
 * 기능 : 회원 마이페이지 주문 요약 응답
 * 설명 : 일반·수제 주문의 진행 중 주문과 픽업 완료 주문을 구분한 조회 계약이다.
 * ******************************
 */
public record OrderMemberSummaryView(
        List<OrderMemberOrderView> inProgressOrders,
        List<OrderMemberOrderView> completedOrders
) {
}
