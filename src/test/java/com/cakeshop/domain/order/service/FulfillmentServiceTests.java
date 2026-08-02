package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 15, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderMapper orderMapper;

    private FulfillmentService fulfillmentService;

    @BeforeEach
    void setUp() {
        fulfillmentService = new FulfillmentService(orderMapper, CLOCK);
    }

    @Test
    void markPickedUp_readyOrder_recordsAdminAndTime() {
        when(orderMapper.markPickedUpIfReady(10L, 7L, NOW)).thenReturn(1);

        fulfillmentService.markPickedUp(10L, 7L);

        verify(orderMapper).markPickedUpIfReady(10L, 7L, NOW);
    }

    @Test
    void markPickedUp_nonReadyOrder_rejectsStateTransition() {
        when(orderMapper.markPickedUpIfReady(10L, 7L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> fulfillmentService.markPickedUp(10L, 7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_STATUS_TRANSITION)
                );
    }
}
