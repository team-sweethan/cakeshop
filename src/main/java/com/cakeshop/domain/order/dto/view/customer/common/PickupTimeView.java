package com.cakeshop.domain.order.dto.view.customer.common;

import java.time.LocalDateTime;

/** 주문 폼에 바인딩할 실제 픽업 가능 시각이다. */
public record PickupTimeView(
        LocalDateTime value,
        String label
) {
}
