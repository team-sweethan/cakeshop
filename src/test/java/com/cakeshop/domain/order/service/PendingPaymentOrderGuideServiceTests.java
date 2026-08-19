package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingPaymentOrderGuideServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T03:00:00Z"), ZoneId.of("Asia/Seoul")
    );

    @Mock private OrderMapper orderMapper;

    private PendingPaymentOrderGuideService service;

    @BeforeEach
    void setUp() {
        service = new PendingPaymentOrderGuideService(orderMapper, CLOCK);
    }

    @Test
    void verifyNewOrderIntentTarget_rejectsAnotherMembersOrder() {
        Order order = pendingOrder(20L, NOW.plusMinutes(5));
        when(orderMapper.findOrderById(42L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyNewOrderIntentTarget(10L, 42L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void verifyNewOrderIntentTarget_rejectsExpiredPendingOrder() {
        Order order = pendingOrder(10L, NOW);
        when(orderMapper.findOrderById(42L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyNewOrderIntentTarget(10L, 42L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private Order pendingOrder(long memberId, LocalDateTime paymentExpiresAt) {
        Order order = new Order();
        order.setMemberId(memberId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentExpiresAt(paymentExpiresAt);
        return order;
    }
}
