package com.cakeshop.domain.product.entity;

import lombok.Getter;

/** 상품 옵션 그룹과 개별 옵션의 노출 상태. */
@Getter
public enum ProductOptionStatus {

    ACTIVE("활성"),
    INACTIVE("비활성");

    private final String displayName;

    ProductOptionStatus(String displayName) {
        this.displayName = displayName;
    }
}
