package com.cakeshop.domain.dashboard.dto.view;

import java.math.BigDecimal;
import java.util.List;

/** 관리자 대시보드에 표시할 지표를 전달한다. */
public record DashboardView(
        long todayOrderCount,
        BigDecimal todaySalesAmount,
        long approvalPendingCount,
        long paymentAttentionCount,
        long inProductionCount,
        long todayPickupCount,
        long lowStockProductCount,
        long pendingReportedPostCount,
        List<TodayPickupScheduleView> todayPickups,
        List<RecentOrderView> recentOrders,
        List<LowStockProductView> lowStockProducts
) {
}
