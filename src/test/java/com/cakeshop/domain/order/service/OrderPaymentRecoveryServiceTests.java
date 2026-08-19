package com.cakeshop.domain.order.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.payment.OrderPaymentRecoveryService;
import com.cakeshop.domain.product.service.ProductStockService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPaymentRecoveryServiceTests {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 8, 7, 12, 0);

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductStockService productStockService;

    @Mock
    private CouponOrderCommandService couponOrderCommandService;

    @InjectMocks
    private OrderPaymentRecoveryService orderPaymentRecoveryService;

    @Test
    void cancelAfterPaymentCompensation_readyOrder_restoresCoupon() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderMapper.cancelAfterPaymentCompensation(10L, CANCELED_AT, "PG 승인 보상 취소"))
                .thenReturn(1);
        when(orderMapper.findStockDeductedItemsForRestore(10L)).thenReturn(java.util.List.of());

        orderPaymentRecoveryService.cancelAfterPaymentCompensation(
                10L,
                CANCELED_AT,
                "PG 승인 보상 취소"
        );

        verify(couponOrderCommandService).restoreCouponForCanceledOrder(10L);
    }

    @Test
    void cancelAfterPaymentCompensation_customUnderReviewOrder_restoresDeductedStock() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.UNDER_REVIEW);
        OrderItem deductedItem = new OrderItem();
        deductedItem.setId(100L);
        deductedItem.setProductId(200L);
        deductedItem.setQuantity(2);
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderMapper.cancelAfterPaymentCompensation(10L, CANCELED_AT, "PG 승인 보상 취소"))
                .thenReturn(1);
        when(orderMapper.findStockDeductedItemsForRestore(10L)).thenReturn(List.of(deductedItem));
        when(orderMapper.markStockRestoredIfDeducted(100L, CANCELED_AT)).thenReturn(1);

        orderPaymentRecoveryService.cancelAfterPaymentCompensation(
                10L,
                CANCELED_AT,
                "PG 승인 보상 취소"
        );

        verify(couponOrderCommandService).restoreCouponForCanceledOrder(10L);
        verify(productStockService).restoreStock(200L, 2);
        verify(orderMapper).markStockRestoredIfDeducted(100L, CANCELED_AT);
    }
}
