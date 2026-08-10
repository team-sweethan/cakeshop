package com.cakeshop.domain.statistics.dto.view;

import java.time.LocalDate;

/** 주문 생성일별 총 주문, 완료 주문과 취소 주문 집계 결과를 전달한다. */
public record DailyOrderStatisticsView(
        LocalDate date,
        long totalOrderCount,
        long completedOrderCount,
        long canceledOrderCount
) {
}
