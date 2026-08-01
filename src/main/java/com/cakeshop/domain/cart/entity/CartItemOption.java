package com.cakeshop.domain.cart.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 장바구니에 담을 당시 검증된 상품 옵션 스냅샷이다. */
@Getter
@Setter
public class CartItemOption {

    private Long id;
    private Long cartItemId;
    private Long productOptionId;
    private String optionName;
    private BigDecimal additionalPrice;
}
