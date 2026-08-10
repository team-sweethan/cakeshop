package com.cakeshop.domain.order.dto.view.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 일반 상품 주문서에 표시할 상품, 금액, 픽업 가능 일시를 조합한 조회 결과다. */
public record GeneralOrderCheckoutView(
        long productId,
        String productName,
        String productTypeLabel,
        int quantity,
        List<SelectedOptionView> selectedOptions,
        BigDecimal productAmount,
        BigDecimal optionAmount,
        BigDecimal totalAmount,
        List<PickupDateView> pickupDates
) {

    /** 주문서에 표시할 선택 옵션이다. */
    public record SelectedOptionView(
            long id,
            String groupName,
            String name,
            BigDecimal additionalPrice
    ) {
    }

    /** 날짜별 픽업 가능 시간 목록이다. */
    public record PickupDateView(
            LocalDate date,
            String label,
            List<PickupTimeView> times
    ) {
    }

    /** 주문 폼에 바인딩할 실제 픽업 가능 시각이다. */
    public record PickupTimeView(
            LocalDateTime value,
            String label
    ) {
    }
}
