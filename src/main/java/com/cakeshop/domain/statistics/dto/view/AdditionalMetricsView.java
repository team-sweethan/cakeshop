package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;

/** 조회 기간의 활동·금액 지표 합계를 전달한다. */
public record AdditionalMetricsView(
        long newMemberCount,
        long withdrawnMemberCount,
        long newPostCount,
        long couponUsageCount,
        BigDecimal refundAmount,
        BigDecimal averageOrderAmount
) {
}
