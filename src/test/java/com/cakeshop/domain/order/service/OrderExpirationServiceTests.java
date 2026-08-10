package com.cakeshop.domain.order.service;

import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.mapper.OrderMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T01:00:00Z"),
            SEOUL
    );

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private CouponOrderCommandService couponOrderCommandService;

    private OrderExpirationService service;

    @BeforeEach
    void setUp() {
        service = new OrderExpirationService(orderMapper, CLOCK, couponOrderCommandService);
        ReflectionTestUtils.setField(service, "batchSize", 100);
    }

    @Test
    void expireOverdueOrders_expiresOnlyStillPendingOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 10, 0);
        when(orderMapper.findOverduePendingOrderIds(now, 100))
                .thenReturn(List.of(1L, 2L));
        when(orderMapper.expireIfPendingPayment(1L, now)).thenReturn(2);
        // 조회 후 결제가 DONE으로 바뀐 경합 상황: 조건부 UPDATE가 0을 반환한다.
        when(orderMapper.expireIfPendingPayment(2L, now)).thenReturn(0);

        assertThat(service.expireOverdueOrders()).isEqualTo(1);

        InOrder inOrder = inOrder(orderMapper);
        inOrder.verify(orderMapper).findOverduePendingOrderIds(now, 100);
        inOrder.verify(orderMapper).expireIfPendingPayment(1L, now);
        inOrder.verify(orderMapper).expireIfPendingPayment(2L, now);
    }
}
