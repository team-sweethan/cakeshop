package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 결제 승인일별 현재 유효한 매출 집계 결과를 전달한다. */
public record DailySalesStatisticsView(
        LocalDate date,
        BigDecimal salesAmount
) {
}
