package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;

/** 조회 기간에 합산한 상품별 통계와 매출 순위를 전달한다. */
public record ProductStatisticsView(
        long ranking,
        long productId,
        String productName,
        long orderCount,
        long salesQuantity,
        BigDecimal salesAmount
) {
}
