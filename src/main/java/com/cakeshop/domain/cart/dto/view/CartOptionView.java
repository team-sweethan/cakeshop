package com.cakeshop.domain.cart.dto.view;

import java.math.BigDecimal;

public record CartOptionView(
        long optionId,
        String name,
        BigDecimal additionalPrice
) {
}
