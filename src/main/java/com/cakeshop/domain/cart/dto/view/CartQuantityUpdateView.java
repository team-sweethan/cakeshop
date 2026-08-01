package com.cakeshop.domain.cart.dto.view;

import java.math.BigDecimal;

public record CartQuantityUpdateView(
        long itemId,
        int quantity,
        BigDecimal itemTotal,
        int totalQuantity,
        BigDecimal baseTotal,
        BigDecimal optionTotal,
        BigDecimal grandTotal
) {

    public static CartQuantityUpdateView from(CartView cart, long itemId) {
        CartItemView item = cart.items().stream()
                .filter(candidate -> candidate.id() == itemId)
                .findFirst()
                .orElseThrow();

        return new CartQuantityUpdateView(
                item.id(), item.quantity(), item.totalPrice(), cart.totalQuantity(),
                cart.baseTotal(), cart.optionTotal(), cart.grandTotal());
    }
}
