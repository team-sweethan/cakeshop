package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 관리자 기간별 통계의 조회 기간, 요약 지표와 기간별 추이를 전달한다. */
public record PeriodStatisticsView(
        LocalDate startDate,
        LocalDate endDate,
        long totalOrderCount,
        long completedOrderCount,
        long canceledOrderCount,
        BigDecimal totalSalesAmount,
        List<StatisticsTrendView> trends,
        boolean aggregationDelayed
) {

    public PeriodStatisticsView(
            LocalDate startDate,
            LocalDate endDate,
            long totalOrderCount,
            long completedOrderCount,
            long canceledOrderCount,
            BigDecimal totalSalesAmount,
            List<StatisticsTrendView> trends
    ) {
        this(
                startDate,
                endDate,
                totalOrderCount,
                completedOrderCount,
                canceledOrderCount,
                totalSalesAmount,
                trends,
                false
        );
    }
}
