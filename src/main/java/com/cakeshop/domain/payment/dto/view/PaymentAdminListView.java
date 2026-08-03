package com.cakeshop.domain.payment.dto.view;

import com.cakeshop.domain.payment.entity.PaymentStatus;

import java.util.List;

/** 관리자 결제 목록과 전체 요약, 적용된 상태 검색 조건이다. */
public record PaymentAdminListView(
        PaymentStatus selectedStatus,
        PaymentAdminSummaryView summary,
        List<PaymentAdminListRow> payments
) {
}
