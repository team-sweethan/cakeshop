package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

/** DB의 product_option_groups 한 행을 표현한다. */
@Getter
@Setter
public class ProductOptionGroup {

    private Long id;
    private Long productId;
    private String name;
    private boolean required;
    private ProductOptionSelectionType selectionType;
    private ProductOptionStatus status;
    private Integer sortOrder;
}
