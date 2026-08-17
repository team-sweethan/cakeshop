package com.cakeshop.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.dashboard.dto.view.LowStockProductView;
import com.cakeshop.domain.dashboard.dto.view.RecentOrderView;
import com.cakeshop.domain.dashboard.dto.view.TodayPickupScheduleView;
import com.cakeshop.domain.dashboard.mapper.DashboardReadModelMapper;
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
        when(dashboardReadModelMapper.countApprovalPendingCustomOrders()).thenReturn(5L);
        when(dashboardReadModelMapper.countPaymentsRequiringAttention()).thenReturn(2L);
        when(dashboardReadModelMapper.countCustomOrdersInProduction()).thenReturn(7L);
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
        RecentOrderView recentOrder = new RecentOrderView(
                20L,
                "ORDER-20",
                "초코 케이크",
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("40000")
        );
        when(dashboardReadModelMapper.findRecentOrders(5))
                .thenReturn(List.of(recentOrder));
        LowStockProductView lowStockProduct = new LowStockProductView(
                30L,
                "재고 부족 케이크",
                1
        );
        when(dashboardReadModelMapper.countLowStockProducts()).thenReturn(6L);
        when(dashboardReadModelMapper.findLowStockProducts(5))
                .thenReturn(List.of(lowStockProduct));
        when(dashboardReadModelMapper.countPendingReportedPosts()).thenReturn(8L);
        DashboardReadModelQueryService service =
                new DashboardReadModelQueryService(dashboardReadModelMapper, CLOCK);

        DashboardView dashboard = service.getDashboard();

        assertThat(dashboard.queryReferenceAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 7, 0, 30));
        assertThat(dashboard.todayOrderCount()).isEqualTo(3L);
        assertThat(dashboard.todaySalesAmount()).isEqualByComparingTo("120000");
        assertThat(dashboard.approvalPendingCount()).isEqualTo(5L);
        assertThat(dashboard.paymentAttentionCount()).isEqualTo(2L);
        assertThat(dashboard.inProductionCount()).isEqualTo(7L);
        assertThat(dashboard.todayPickupCount()).isEqualTo(4L);
        assertThat(dashboard.lowStockProductCount()).isEqualTo(6L);
        assertThat(dashboard.pendingReportedPostCount()).isEqualTo(8L);
        assertThat(dashboard.todayPickups()).containsExactly(pickup);
        assertThat(dashboard.recentOrders()).containsExactly(recentOrder);
        assertThat(dashboard.lowStockProducts()).containsExactly(lowStockProduct);
        verify(dashboardReadModelMapper).countTodayOrders(start, end);
        verify(dashboardReadModelMapper).sumTodaySales(start, end);
        verify(dashboardReadModelMapper).countApprovalPendingCustomOrders();
        verify(dashboardReadModelMapper).countPaymentsRequiringAttention();
        verify(dashboardReadModelMapper).countCustomOrdersInProduction();
        verify(dashboardReadModelMapper).countTodayPickups(start, end);
        verify(dashboardReadModelMapper).findTodayPickupSchedules(start, end, 5);
        verify(dashboardReadModelMapper).findRecentOrders(5);
        verify(dashboardReadModelMapper).countLowStockProducts();
        verify(dashboardReadModelMapper).findLowStockProducts(5);
        verify(dashboardReadModelMapper).countPendingReportedPosts();
    }
}
