package com.cakeshop.domain.product.customer.dto.form;

import lombok.Getter;

/**
 * 고객 상품 목록의 정렬 조건.
 */
@Getter
public enum ProductSort {

    /** 후기 수와 평균 평점이 높은 상품 우선. */
    POPULAR("인기순"),

    /** 최근 등록된 상품 우선. */
    NEWEST("최신순"),

    /** 기본 가격이 낮은 상품 우선. */
    PRICE_ASC("가격 낮은순"),

    /** 기본 가격이 높은 상품 우선. */
    PRICE_DESC("가격 높은순");

    private final String displayName;

    ProductSort(String displayName) {
        this.displayName = displayName;
    }
}