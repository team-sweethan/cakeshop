package com.cakeshop.domain.product.entity;

/**
 * 상품의 판매 상태.
 * enum 이름은 products.status 컬럼에 그대로 저장한다.
 */
public enum ProductStatus {
    ACTIVE,   // 판매 중
    INACTIVE  // 판매 중지
}
