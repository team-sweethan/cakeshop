package com.cakeshop.domain.product.dto.form;

import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 상품 목록의 검색 및 필터 조건을 담는다.
 */
@Getter
@Setter
public class ProductAdminSearchCondition {

    /** 상품명 검색어. */
    private String keyword;

    /** 상품 유형 필터. */
    private ProductType type;

    /** 판매 상태 필터. */
    private ProductStatus status;

    /** 재고 상태 필터. */
    private AdminStockFilter stock;

    /**
     * 검색어 앞뒤의 공백을 제거한다.
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