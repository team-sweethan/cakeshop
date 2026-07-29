package com.cakeshop.domain.product.admin.dto.view;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductOptionStatus;

/** 관리자 화면에 표시할 개별 상품 옵션. */
public record ProductOptionAdminView(
        Long id,
        String name,
        BigDecimal additionalPrice,
        ProductOptionStatus status,
        Integer sortOrder
) {
}
