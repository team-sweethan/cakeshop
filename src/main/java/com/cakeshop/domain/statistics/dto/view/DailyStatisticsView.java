package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 관리자 기간별 통계의 날짜별 주문 수와 매출을 전달한다. */
public record DailyStatisticsView(
        LocalDate date,
        long orderCount,
        BigDecimal salesAmount
) {
}
