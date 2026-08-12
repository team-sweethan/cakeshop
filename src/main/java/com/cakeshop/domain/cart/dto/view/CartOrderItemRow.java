package com.cakeshop.domain.cart.dto.view;

/** CartOrderMapper가 장바구니 기본 행을 읽을 때만 사용하는 내부 조회 결과다. */
public record CartOrderItemRow(
        long cartItemId,
        long productId,
        int quantity,
        String requirements
) {
}
