package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

/**
 * 고객 상품 상세 화면에 표시할 개별 상품 옵션.
 *
 * @param id 옵션 식별자
 * @param name 옵션 이름
 * @param additionalPrice 옵션 추가 금액
 */
public record ProductOptionItemView(
        Long id,
        String name,
        BigDecimal additionalPrice
) {
}
