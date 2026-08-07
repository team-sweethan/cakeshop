package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.mapper.DashboardReadModelMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardReadModelQueryService {

    private final DashboardReadModelMapper dashboardReadModelMapper;
    private final Clock clock;

    /** 관리자 대시보드에 표시할 지표를 조회한다. */
    @Transactional(readOnly = true)
    public StatisticsDashboardView getDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        long todayOrderCount = dashboardReadModelMapper.countTodayOrders(start, end);

        return new StatisticsDashboardView(todayOrderCount);
    }
}
