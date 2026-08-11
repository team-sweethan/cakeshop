package com.cakeshop.domain.order.service.admin;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 주문제작 승인과 제작 완료 상태 전이를 담당한다. */
@Service
@RequiredArgsConstructor
public class AdminCustomOrderService {

    private final OrderMapper orderMapper;
    private final Clock clock;

    /** UNDER_REVIEW 수제 주문을 승인하고 제작을 시작한다. */
    @Transactional
    public void startProduction(long orderId, long adminMemberId) {
        Order order = findCustomOrderForUpdate(orderId);
        if (adminMemberId <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        requireTransition(order, OrderStatus.IN_PRODUCTION);
        requireOneRow(orderMapper.startProductionIfUnderReview(
                orderId,
                adminMemberId,
                LocalDateTime.now(clock)
        ));
    }

    /** IN_PRODUCTION 수제 주문을 제작 완료 후 픽업 대기로 변경한다. */
    @Transactional
    public void completeProduction(long orderId, long adminMemberId) {
        Order order = findCustomOrderForUpdate(orderId);
        if (adminMemberId <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        requireTransition(order, OrderStatus.READY_FOR_PICKUP);
        requireOneRow(orderMapper.markReadyForPickupIfInProduction(
                orderId,
                LocalDateTime.now(clock)
        ));
    }

    private Order findCustomOrderForUpdate(long orderId) {
        if (orderId <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        Order order = orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (order.getOrderType() != OrderType.CUSTOM) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        return order;
    }

    private void requireTransition(Order order, OrderStatus next) {
        if (order.getStatus() == null || !order.getStatus().canTransitionTo(next)) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
