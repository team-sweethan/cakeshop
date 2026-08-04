package com.cakeshop.domain.product.dto.form;

import lombok.Getter;

/**
 * 고객 상품 목록의 재고 검색 조건.
 */
@Getter
public enum StockFilter {

    /** 재고 제한이 없거나 재고가 1개 이상인 상품. */
    AVAILABLE("주문 가능"),

    /** 재고 수량이 0인 상품. */
    OUT_OF_STOCK("품절");

    private final String displayName;

    StockFilter(String displayName) {
        this.displayName = displayName;
    }
}