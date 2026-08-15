package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderMemberOrderRow;
import com.cakeshop.domain.order.dto.view.OrderMemberOrderView;
import com.cakeshop.domain.order.dto.view.OrderMemberSummaryView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMemberMapper;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 수민
 * 담당자 : 주환
 * 작성일 : 2026-08-15
 * 기능 : 회원 마이페이지 주문 요약 조회
 * 설명 : 회원 도메인이 마이페이지에 주문 요약을 표시하도록 제공하는 공개 Query 계약이다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class OrderMemberQueryService {

    private static final int MY_PAGE_ORDER_LIMIT = 5;

    private final OrderMemberMapper orderMemberMapper;

    /**
     * ******************************
     * 작성자 : 수민
     * 담당자 : 주환
     * 작성일 : 2026-08-15
     * 기능 : 회원별 최근 주문 현황 조회
     * 설명 : 일반·수제 주문의 진행 중 주문과 픽업 완료 주문을 각각 최근 5건까지 조회한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public OrderMemberSummaryView getMyPageOrders(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_AVAILABLE);
        }

        return new OrderMemberSummaryView(
                orderMemberMapper.findInProgressOrders(memberId, MY_PAGE_ORDER_LIMIT)
                        .stream()
                        .map(this::toView)
                        .toList(),
                orderMemberMapper.findCompletedOrders(memberId, MY_PAGE_ORDER_LIMIT)
                        .stream()
                        .map(this::toView)
                        .toList()
        );
    }

    private OrderMemberOrderView toView(OrderMemberOrderRow row) {
        return new OrderMemberOrderView(
                row.orderId(),
                row.orderNumber(),
                orderTypeLabel(row.orderType()),
                row.productName(),
                row.itemCount(),
                statusLabel(row.status()),
                row.finalAmount(),
                row.pickupAt(),
                row.createdAt()
        );
    }

    private String orderTypeLabel(OrderType orderType) {
        return orderType == OrderType.CUSTOM ? "주문 제작" : "일반 상품";
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> "결제 대기";
            case UNDER_REVIEW -> "승인 대기";
            case IN_PRODUCTION -> "제작 중";
            case READY_FOR_PICKUP -> "픽업 준비";
            case PICKED_UP -> "픽업 완료";
            case CANCELED -> "취소 완료";
            case REJECTED -> "주문 반려";
            case EXPIRED -> "결제 만료";
        };
    }
}
