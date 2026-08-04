package com.cakeshop.domain.product.entity;

import lombok.Getter;
import lombok.Setter;

/** DB의 product_images 한 행을 표현한다. */
@Getter
@Setter
public class ProductImage {

    private Long id;
    private Long productId;
    private String imageUrl;
    private Integer sortOrder;
}
