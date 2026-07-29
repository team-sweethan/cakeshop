package com.cakeshop.domain.product.entity;

import lombok.Getter;

/** 옵션 그룹에서 고객이 선택할 수 있는 방식. */
@Getter
public enum ProductOptionSelectionType {

    SINGLE("하나만 선택"),
    MULTIPLE("여러 개 선택");

    private final String displayName;

    ProductOptionSelectionType(String displayName) {
        this.displayName = displayName;
    }
}
