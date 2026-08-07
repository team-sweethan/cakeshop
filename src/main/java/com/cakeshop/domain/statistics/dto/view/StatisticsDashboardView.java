package com.cakeshop.domain.statistics.dto.view;

/** 관리자 대시보드에 표시할 지표를 전달한다. */
public record StatisticsDashboardView(
        long todayOrderCount
) {
}
