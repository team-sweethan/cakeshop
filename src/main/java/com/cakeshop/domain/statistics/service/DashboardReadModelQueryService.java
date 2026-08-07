package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.RecentOrderView;
import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.dto.view.TodayPickupScheduleView;
import com.cakeshop.domain.statistics.mapper.DashboardReadModelMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardReadModelQueryService {

    private static final int DASHBOARD_LIST_LIMIT = 5;

    private final DashboardReadModelMapper dashboardReadModelMapper;
    private final Clock clock;

    /** 관리자 대시보드에 표시할 지표를 조회한다. */
    @Transactional(readOnly = true)
    public StatisticsDashboardView getDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // 오늘 주문
        long todayOrderCount = dashboardReadModelMapper.countTodayOrders(start, end);
        // 오늘 매출
        BigDecimal todaySalesAmount = dashboardReadModelMapper.sumTodaySales(start, end);
        // 확인 필요 결제
        long paymentAttentionCount =
                dashboardReadModelMapper.countPaymentsRequiringAttention();
        // 오늘 픽업 예정
        long todayPickupCount = dashboardReadModelMapper.countTodayPickups(start, end);
        // 오늘 픽업 일정
        List<TodayPickupScheduleView> todayPickups =
                dashboardReadModelMapper.findTodayPickupSchedules(
                        start,
                        end,
                        DASHBOARD_LIST_LIMIT
                );
        // 최근 주문
        List<RecentOrderView> recentOrders =
                dashboardReadModelMapper.findRecentOrders(DASHBOARD_LIST_LIMIT);

        return new StatisticsDashboardView(
                todayOrderCount,
                todaySalesAmount,
                paymentAttentionCount,
                todayPickupCount,
                todayPickups,
                recentOrders
        );
    }
}
