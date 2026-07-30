package com.cakeshop.domain.product.entity;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** DB의 product_options 한 행을 표현한다. */
@Getter
@Setter
public class ProductOption {

    private Long id;
    private Long optionGroupId;
    private String name;
    private BigDecimal additionalPrice;
    private ProductOptionStatus status;
    private Integer sortOrder;
}
