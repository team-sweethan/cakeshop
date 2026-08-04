package com.cakeshop.domain.cart.entity;

import lombok.Getter;
import lombok.Setter;

/** 주문 제작 장바구니 항목에 첨부된 참고 이미지다. */
@Getter
@Setter
public class CartItemImage {

    private Long id;
    private Long cartItemId;
    private String imageUrl;
    private Integer sortOrder;
}
