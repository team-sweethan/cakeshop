package com.cakeshop.domain.cart.dto.view;

/** 결제 완료 후 주문 시점 수량과 일치할 때만 삭제할 장바구니 항목 정보다. */
public record CartPaymentItemTarget(long cartItemId, int quantity) {
}
