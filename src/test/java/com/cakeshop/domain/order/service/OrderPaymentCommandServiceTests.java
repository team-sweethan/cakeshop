package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPaymentCommandServiceTests {

    @Mock
    private OrderMapper orderMapper;

    private OrderPaymentCommandService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentCommandService(orderMapper);
    }

    @Test
    void completeGeneralOrderAfterPayment_pendingOrder_marksReady() {
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 1, 10, 1);
        when(orderMapper.markReadyForPickupAfterPaymentIfPending(1L, approvedAt)).thenReturn(1);

        service.completeGeneralOrderAfterPayment(1L, approvedAt);

        verify(orderMapper).markReadyForPickupAfterPaymentIfPending(1L, approvedAt);
    }

    @Test
    void lockGeneralOrderForPayment_pendingGeneralOrder_locksOrderRow() {
        when(orderMapper.findOrderByIdForUpdate(1L)).thenReturn(Optional.of(pendingGeneralOrder()));

        service.lockGeneralOrderForPayment(1L);

        verify(orderMapper).findOrderByIdForUpdate(1L);
    }

    @Test
    void lockGeneralOrderForPayment_expiredOrder_rejectsCompletion() {
        Order expired = pendingGeneralOrder();
        expired.setStatus(OrderStatus.EXPIRED);
        when(orderMapper.findOrderByIdForUpdate(1L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.lockGeneralOrderForPayment(1L))
                .isInstanceOf(BusinessException.class);
    }

    private Order pendingGeneralOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderType(OrderType.GENERAL);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return order;
    }
}
