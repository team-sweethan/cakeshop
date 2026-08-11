package com.cakeshop.domain.order.service.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
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
class AdminCustomOrderServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 15, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T06:00:00Z"), ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderMapper orderMapper;

    private AdminCustomOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminCustomOrderService(orderMapper, CLOCK);
    }

    @Test
    void startProduction_underReviewCustomOrder_recordsApproval() {
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(custom(OrderStatus.UNDER_REVIEW)));
        when(orderMapper.startProductionIfUnderReview(10L, 7L, NOW)).thenReturn(1);

        service.startProduction(10L, 7L);

        verify(orderMapper).startProductionIfUnderReview(10L, 7L, NOW);
    }

    @Test
    void completeProduction_underReviewOrder_rejectsDirectSkip() {
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(custom(OrderStatus.UNDER_REVIEW)));

        assertThatThrownBy(() -> service.completeProduction(10L, 7L))
                .isInstanceOf(BusinessException.class);
    }

    private Order custom(OrderStatus status) {
        Order order = new Order();
        order.setId(10L);
        order.setOrderType(OrderType.CUSTOM);
        order.setStatus(status);
        return order;
    }
}
