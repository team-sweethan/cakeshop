package com.cakeshop.domain.product.dto.form;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductType;

import lombok.Getter;
import lombok.Setter;

/**
 * 고객 상품 목록의 검색·필터·정렬 조건을 담는다.
 */
@Getter
@Setter
public class ProductSearchCondition {

    /** 상품명 검색어. */
    private String keyword;

    /** 일반 케이크 또는 주문 제작 상품 유형. */
    private ProductType type;

    /** 최소 가격. 기본값은 0원이다. */
    private BigDecimal minPrice = BigDecimal.ZERO;

    /** 최대 가격. 기본값은 10만원이다. */
    private BigDecimal maxPrice = BigDecimal.valueOf(100_000);

    /** 주문 가능 또는 품절 재고 조건. */
    private StockFilter stock;

    /** 상품 정렬 조건. */
    private ProductSort sort = ProductSort.POPULAR;

    /**
     * 검색어 앞뒤의 불필요한 공백을 제거한다.
     *
     * @return 정리된 검색어 또는 검색어가 없으면 {@code null}
     */
    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
