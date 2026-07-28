package com.cakeshop.domain.product.admin.dto.form;

import lombok.Getter;

/**
 * 관리자 상품 목록에서 사용하는 재고 필터.
 */
@Getter
public enum AdminStockFilter {

    /** 재고 제한이 없거나 재고가 남아 있는 상품. */
    AVAILABLE("재고 있음"),

    /** 재고 수량이 0인 상품. */
    OUT_OF_STOCK("품절");

    private final String displayName;

    AdminStockFilter(String displayName) {
        this.displayName = displayName;
    }
}