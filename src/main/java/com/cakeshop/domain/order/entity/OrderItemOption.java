package com.cakeshop.domain.order.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** DB의 order_item_options 한 행을 표현하는 주문 당시 옵션 스냅샷이다. */
@Getter
@Setter
public class OrderItemOption {

    private Long id;
    //OrderItem.id
    private Long orderItemId;
    //ProductOption.id
    private Long productOptionId;
    private String optionGroupName;
    private String optionName;
    private BigDecimal additionalPrice;
}
