package com.cakeshop.domain.order.dto.view.customer.common;

import java.time.LocalDate;
import java.util.List;

/** 날짜별 픽업 가능 시간 목록이다. */
public record PickupDateView(
        LocalDate date,
        String label,
        List<PickupTimeView> times
) {
}
