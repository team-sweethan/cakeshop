package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.dto.view.TodayPickupScheduleView;
import com.cakeshop.domain.statistics.mapper.DashboardReadModelMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardReadModelQueryServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T15:30:00Z"),
            SEOUL
    );

    @Mock
    private DashboardReadModelMapper dashboardReadModelMapper;

    @Test
    void getDashboard_seoulDate_returnsDashboardMetrics() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 7, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 8, 0, 0);
        when(dashboardReadModelMapper.countTodayOrders(start, end)).thenReturn(3L);
        when(dashboardReadModelMapper.sumTodaySales(start, end))
                .thenReturn(new BigDecimal("120000"));
        when(dashboardReadModelMapper.countPaymentsRequiringAttention()).thenReturn(2L);
        TodayPickupScheduleView pickup = new TodayPickupScheduleView(
                10L,
                start.plusHours(11),
                "ORDER-10",
                "딸기 케이크",
                OrderStatus.READY_FOR_PICKUP
        );
        when(dashboardReadModelMapper.countTodayPickups(start, end)).thenReturn(4L);
        when(dashboardReadModelMapper.findTodayPickupSchedules(start, end, 5))
                .thenReturn(List.of(pickup));
        DashboardReadModelQueryService service =
                new DashboardReadModelQueryService(dashboardReadModelMapper, CLOCK);

        StatisticsDashboardView dashboard = service.getDashboard();

        assertThat(dashboard.todayOrderCount()).isEqualTo(3L);
        assertThat(dashboard.todaySalesAmount()).isEqualByComparingTo("120000");
        assertThat(dashboard.paymentAttentionCount()).isEqualTo(2L);
        assertThat(dashboard.todayPickupCount()).isEqualTo(4L);
        assertThat(dashboard.todayPickups()).containsExactly(pickup);
        verify(dashboardReadModelMapper).countTodayOrders(start, end);
        verify(dashboardReadModelMapper).sumTodaySales(start, end);
        verify(dashboardReadModelMapper).countPaymentsRequiringAttention();
        verify(dashboardReadModelMapper).countTodayPickups(start, end);
        verify(dashboardReadModelMapper).findTodayPickupSchedules(start, end, 5);
    }
}
