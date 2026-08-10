package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;

/** 원본 주문·결제에서 조회한 날짜별 통계 집계값이다. */
public record DailyStatisticsSourceView(
        long totalOrderCount,
        long completedOrderCount,
        long canceledOrderCount,
        BigDecimal totalSalesAmount
) {
}
