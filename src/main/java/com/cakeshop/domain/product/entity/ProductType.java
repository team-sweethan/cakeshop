package com.cakeshop.domain.product.entity;

import lombok.Getter;

/**
 * 상품의 주문 유형.
 *
 * <p>enum 이름은 products.product_type 컬럼에 저장하고,
 * displayName은 고객 및 관리자 화면에 표시한다.</p>
 */
@Getter
public enum ProductType {

    /** 일반 케이크 상품. */
    GENERAL("일반 케이크"),

    /** 고객의 요청에 따라 제작하는 주문 제작 상품. */
    CUSTOM("주문 제작");

    /** 화면에 표시할 상품 유형 이름. */
    private final String displayName;

    /**
     * 상품 유형의 화면 표시명을 설정한다.
     *
     * @param displayName 화면에 표시할 이름
     */
    ProductType(String displayName) {
        this.displayName = displayName;
    }
}