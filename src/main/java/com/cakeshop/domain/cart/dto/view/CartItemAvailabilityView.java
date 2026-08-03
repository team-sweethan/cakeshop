package com.cakeshop.domain.cart.dto.view;

public record CartItemAvailabilityView(
        long itemId,
        boolean available
) {
}
