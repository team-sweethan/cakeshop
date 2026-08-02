package com.cakeshop.domain.cart.dto.view;

import java.math.BigDecimal;
import java.util.List;

public record CartView(
        List<CartItemView> items,
        int totalQuantity,
        BigDecimal baseTotal,
        BigDecimal optionTotal,
        BigDecimal grandTotal
) {

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
