package com.cakeshop.domain.order.entity;

import com.cakeshop.domain.product.entity.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** DB의 order_items 한 행을 표현한다. */
@Getter
@Setter
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private ProductType productType;
    private Integer quantity;
    private BigDecimal basePrice;
    private BigDecimal optionAmount;
    private BigDecimal totalAmount;
    private String requirements;
    private Integer preparationDays;
    private Integer cancellationLimitDays;
}
