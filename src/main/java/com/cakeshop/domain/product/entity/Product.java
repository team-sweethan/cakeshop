package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DB의 products 한 행을 표현한다. */
@Getter
@Setter
public class Product {

    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private BigDecimal basePrice;

    /**
     * null: 재고 제한 없음
     * 0: 품절
     * 1 이상: 주문 가능 수량
     */
    private Integer stockQuantity;

    private ProductType productType; // enum

    private Integer preparationDays;

    private ProductStatus status; // enum

    private BigDecimal averageRating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
