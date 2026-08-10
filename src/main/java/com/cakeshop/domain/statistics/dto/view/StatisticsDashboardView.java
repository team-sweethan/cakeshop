package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.util.List;

/** 관리자 대시보드에 표시할 지표를 전달한다. */
public record StatisticsDashboardView(
        long todayOrderCount,
        BigDecimal todaySalesAmount,
        long paymentAttentionCount,
        long todayPickupCount,
        long lowStockProductCount,
        List<TodayPickupScheduleView> todayPickups,
        List<RecentOrderView> recentOrders,
        List<LowStockProductView> lowStockProducts
) {
}
