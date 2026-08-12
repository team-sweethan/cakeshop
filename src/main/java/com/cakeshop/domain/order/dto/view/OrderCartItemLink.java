package com.cakeshop.domain.order.dto.view;

/** 주문 생성 시점 장바구니 항목과 수량을 보존하는 주문 소유 연결 정보다. */
public record OrderCartItemLink(long cartItemId, int quantity) {
}
