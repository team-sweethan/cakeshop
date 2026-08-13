package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;

/** 다른 도메인의 원본 데이터에서 조회한 날짜별 활동·금액 지표 집계값이다. */
public record DailyAdditionalMetricsSourceView(
        long newMemberCount,
        long withdrawnMemberCount,
        long newPostCount,
        long couponUsageCount,
        BigDecimal refundAmount,
        long validPaymentOrderCount
) {
}
