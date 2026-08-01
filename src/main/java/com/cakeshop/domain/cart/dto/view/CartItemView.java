package com.cakeshop.domain.cart.dto.view;

import com.cakeshop.domain.product.entity.ProductType;
import java.math.BigDecimal;
import java.util.List;

public record CartItemView(
        long id,
        long productId,
        String productName,
        ProductType productType,
        int quantity,
        Integer stockQuantity,
        boolean available,
        BigDecimal basePrice,
        BigDecimal optionPrice,
        BigDecimal totalPrice,
        String requirements,
        List<CartOptionView> options
) {
}
