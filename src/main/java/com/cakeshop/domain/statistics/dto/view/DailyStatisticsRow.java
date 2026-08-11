package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 확정 통계 테이블에서 조회한 날짜별 주문·매출 집계다. */
public record DailyStatisticsRow(
        LocalDate date,
        long totalOrderCount,
        long completedOrderCount,
        long canceledOrderCount,
        BigDecimal totalSalesAmount
) {
}
