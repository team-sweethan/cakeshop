package com.cakeshop.domain.cart.dto.view;

import java.math.BigDecimal;
import java.util.List;

public record CartQuantityUpdateView(
        long itemId,
        int quantity,
        boolean available,
        BigDecimal itemTotal,
        int itemCount,
        BigDecimal baseTotal,
        BigDecimal optionTotal,
        BigDecimal grandTotal,
        List<CartItemAvailabilityView> itemAvailability
) {

    public static CartQuantityUpdateView from(CartView cart, long itemId) {
        CartItemView item = cart.items().stream()
                .filter(candidate -> candidate.id() == itemId)
                .findFirst()
                .orElseThrow();

        return new CartQuantityUpdateView(
                item.id(), item.quantity(), item.available(), item.totalPrice(), cart.itemCount(),
                cart.baseTotal(), cart.optionTotal(), cart.grandTotal(),
                cart.items().stream()
                        .map(candidate -> new CartItemAvailabilityView(
                                candidate.id(),
                                candidate.available()))
                        .toList());
    }
}
