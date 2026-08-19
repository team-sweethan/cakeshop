package com.cakeshop.domain.order.service.payment;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 결제 완료 연동용 주문 명령 계약
 * 설명 : 결제 도메인이 orders 테이블을 직접 변경하지 않고 결제 완료에 필요한 주문 상태와 재고 차감 기록을 변경하도록 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentCommandService {

    private final OrderMapper orderMapper;

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 결제 완료 대상 주문 잠금
     * 설명 : 결제 만료 처리와 직렬화하도록 PENDING_PAYMENT 주문 행을 잠근다.
     * ******************************
     */
    @Transactional
    public void lockOrderForPayment(long orderId) {
        Order order = orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || (order.getOrderType() != OrderType.GENERAL
                && order.getOrderType() != OrderType.CUSTOM)) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /** 일반 주문 0원 결제 호출부의 호환용 잠금 명령이다. */
    @Transactional
    public void lockGeneralOrderForPayment(long orderId) {
        lockOrderForPayment(orderId);
    }

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 결제 완료 주문 상태 반영
     * 설명 : 조건부 갱신으로 PENDING_PAYMENT 일반 주문만 READY_FOR_PICKUP으로 변경한다.
     * ******************************
     */
    @Transactional
    public void completeGeneralOrderAfterPayment(long orderId, LocalDateTime readyAt) {
        requireOneRow(orderMapper.markReadyForPickupAfterPaymentIfPending(orderId, readyAt));
    }

    /** 결제 완료된 수제 주문을 관리자 검토 대기로 전이한다. */
    @Transactional
    public void completeCustomOrderAfterPayment(long orderId, LocalDateTime underReviewAt) {
        requireOneRow(orderMapper.markUnderReviewAfterPaymentIfPending(orderId, underReviewAt));
    }

    /** 실제 유한 재고를 차감한 주문 항목에만 차감 시각을 한 번 기록한다. */
    @Transactional
    public void recordStockDeduction(long orderItemId, LocalDateTime deductedAt) {
        if (orderItemId <= 0 || deductedAt == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        requireOneRow(orderMapper.markStockDeductedIfUnset(orderItemId, deductedAt));
    }

    /** 기존 일반 주문 결제 호출부의 호환용 재고 차감 기록 명령이다. */
    @Transactional
    public void recordGeneralStockDeduction(long orderItemId, LocalDateTime deductedAt) {
        recordStockDeduction(orderItemId, deductedAt);
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
