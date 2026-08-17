package com.cakeshop.domain.statistics.dto.source;

import java.math.BigDecimal;

/** 원본 주문·결제에서 조회한 날짜별 상품 통계 집계값이다. */
public record DailyProductStatisticsSourceView(
        long productId,
        String productName,
        long orderCount,
        long salesQuantity,
        BigDecimal salesAmount
) {
}
