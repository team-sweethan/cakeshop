package com.cakeshop.domain.payment.dto.view;

/** 관리자 결제 내역 상단에 표시할 전체 상태 요약이다. */
public record PaymentAdminSummaryView(
        long totalCount,
        long doneCount,
        long canceledCount,
        long attentionCount
) {
}
