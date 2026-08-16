package com.cakeshop.domain.order.dto.view.customer.common;

import java.math.BigDecimal;

/** 고객 주문서에 표시할 선택 옵션이다. */
public record CheckoutOptionView(
        long id,
        String groupName,
        String name,
        BigDecimal additionalPrice
) {
}
