package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.mapper.OrderMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomOrderProductionCompletionServiceTests {

    @Mock
    private OrderMapper orderMapper;

    @Test
    void completeDueOrders_conditionallyUpdatedOrdersOnly_countsCompletedOrders() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        CustomOrderProductionCompletionService service =
                new CustomOrderProductionCompletionService(orderMapper, clock);
        when(orderMapper.findDueCustomProductionOrderIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(orderMapper.markReadyForPickupIfInProduction(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(orderMapper.markReadyForPickupIfInProduction(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        assertThat(service.completeDueOrders()).isEqualTo(1);
    }
}
