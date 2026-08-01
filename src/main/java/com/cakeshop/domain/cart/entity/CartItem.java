package com.cakeshop.domain.cart.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 장바구니에 저장된 상품 한 행이다. */
@Getter
@Setter
public class CartItem {

    private Long id;
    private Long cartId;
    private Long productId;
    private Integer quantity;
    private String requirements;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
