package com.cakeshop.domain.order.service;

import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** PG 보상 취소 뒤 주문 취소와 이미 차감된 재고 복구를 담당한다. */
@Service
@RequiredArgsConstructor
public class OrderPaymentRecoveryService {

    private final OrderMapper orderMapper;
    private final ProductStockService productStockService;
    // 쿠폰 담당자의 공개 계약으로, PG 보상 취소 후 주문에 연결된 쿠폰을 복구한다.
    private final CouponOrderCommandService couponOrderCommandService;

    @Transactional
    public void cancelAfterPaymentCompensation(
            long orderId,
            LocalDateTime canceledAt,
            String cancelReason
    ) {
        Order order = orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        OrderErrorCode.INVALID_STATUS_TRANSITION
                ));
        OrderStatus previousStatus = order.getStatus();
        if (previousStatus != OrderStatus.PENDING_PAYMENT
                && previousStatus != OrderStatus.READY_FOR_PICKUP
                && previousStatus != OrderStatus.EXPIRED) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        requireOneRow(orderMapper.cancelAfterPaymentCompensation(
                orderId,
                canceledAt,
                cancelReason
        ));
        // 쿠폰 도메인 공개 계약: PG 보상 취소로 주문이 취소되면 사용 쿠폰도 함께 복구한다.
        couponOrderCommandService.restoreCouponForCanceledOrder(orderId);
        if (previousStatus == OrderStatus.READY_FOR_PICKUP) {
            restoreDeductedStock(orderId, canceledAt);
        }
    }

    private void restoreDeductedStock(long orderId, LocalDateTime restoredAt) {
        List<OrderItem> items = orderMapper.findStockDeductedItemsForRestore(orderId);
        for (OrderItem item : items) {
            if (item.getProductId() == null
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
            }
            productStockService.restoreStock(item.getProductId(), item.getQuantity());
            requireOneRow(orderMapper.markStockRestoredIfDeducted(item.getId(), restoredAt));
        }
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
