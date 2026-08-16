package com.cakeshop.domain.order.entity;

/** 주문 흐름을 구분하는 orders.order_type 값이다. */
public enum OrderType {
    GENERAL("일반 상품"),
    CUSTOM("주문 제작");

    private final String orderTypeLabel;

    OrderType(String orderTypeLabel) {
        this.orderTypeLabel = orderTypeLabel;
    }

    public String orderTypeLabel() {
        return orderTypeLabel;
    }
}
