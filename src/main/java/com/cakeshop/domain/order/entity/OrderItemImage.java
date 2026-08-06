package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

/** DB의 order_item_images 한 행을 표현한다. */
@Getter
@Setter
public class OrderItemImage {

    private Long id;
    //order_item.id
    private Long orderItemId;
    private String imageUrl;
    private Integer sortOrder;
}
