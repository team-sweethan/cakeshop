package com.cakeshop.domain.order.service;

import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 결제 환불 연동용 주문 취소 명령 계약
 * 설명 : 결제 도메인이 orders 테이블을 직접 변경하지 않고 일반 주문 취소·재고·쿠폰 복구를 요청하도록 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentCancellationCommandService {

    private final OrderMapper orderMapper;
    private final ProductStockService productStockService;
    private final CouponOrderCommandService couponOrderCommandService;

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 고객 소유 결제 취소 대상 주문 잠금
     * 설명 : 고객 환불 준비 시 주문 소유권을 검증하고 주문 행을 잠근다.
     * ******************************
     */
    @Transactional
    public void lockCustomerOrderForPaymentCancellation(long memberId, long orderId) {
        Order order = findOrderForUpdate(orderId);
        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 관리자 결제 취소 대상 주문 잠금
     * 설명 : 관리자 환불 준비 시 주문 행을 잠근다.
     * ******************************
     */
    @Transactional
    public void lockOrderForPaymentCancellation(long orderId) {
        findOrderForUpdate(orderId);
    }

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 일반 주문 결제 취소 가능 여부 확인
     * 설명 : 주문 유형·상태·픽업 일시를 기준으로 환불 요청 가능 여부를 확인한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public boolean isGeneralPaymentCancellationAvailable(long orderId, LocalDateTime requestedAt) {
        return orderMapper.findOrderById(orderId)
                .map(order -> isGeneralPaymentCancellationAvailable(order, requestedAt))
                .orElse(false);
    }

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 일반 주문 결제 취소 완료 여부 확인
     * 설명 : PG 취소 재시도 시 주문 취소가 이미 완료됐는지 확인한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public boolean isGeneralPaymentCancellationCompleted(long orderId) {
        return orderMapper.findOrderById(orderId)
                .map(order -> order.getOrderType() == OrderType.GENERAL
                        && order.getStatus() == OrderStatus.CANCELED)
                .orElse(false);
    }

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 일반 주문 결제 취소 완료 반영
     * 설명 : PG 취소 성공 뒤 주문 취소, 쿠폰 복구와 실제 차감 재고 복구를 하나의 트랜잭션에 반영한다.
     *        이미 취소된 주문이면 쿠폰 복구만 멱등하게 다시 수행한다.
     * ******************************
     */
    @Transactional
    public boolean completeGeneralPaymentCancellation(
            long orderId,
            String canceledBy,
            String reason,
            LocalDateTime requestedAt,
            LocalDateTime canceledAt
    ) {
        Order order = findOrderForUpdate(orderId);
        if (order.getOrderType() != OrderType.GENERAL) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            couponOrderCommandService.restoreCouponForCanceledOrder(orderId);
            return true;
        }
        if (!isGeneralPaymentCancellationAvailable(order, requestedAt)) {
            return false;
        }
        if (orderMapper.cancelIfCurrent(
                orderId,
                OrderStatus.READY_FOR_PICKUP,
                canceledBy,
                reason,
                requestedAt,
                canceledAt
        ) != 1) {
            return false;
        }
        couponOrderCommandService.restoreCouponForCanceledOrder(orderId);
        restoreDeductedStock(orderId, canceledAt);
        return true;
    }

    private Order findOrderForUpdate(long orderId) {
        return orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private boolean isGeneralPaymentCancellationAvailable(Order order, LocalDateTime requestedAt) {
        return order.getOrderType() == OrderType.GENERAL
                && order.getStatus() == OrderStatus.READY_FOR_PICKUP
                && requestedAt != null
                && order.getPickupAt() != null
                && requestedAt.isBefore(order.getPickupAt());
    }

    private void restoreDeductedStock(long orderId, LocalDateTime restoredAt) {
        List<OrderItem> items = orderMapper.findStockDeductedItemsForRestore(orderId);
        for (OrderItem item : items) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
            }
            productStockService.restoreStock(item.getProductId(), item.getQuantity());
            if (orderMapper.markStockRestoredIfDeducted(item.getId(), restoredAt) != 1) {
                throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
            }
        }
    }
}
