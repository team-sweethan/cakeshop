package com.cakeshop.domain.product.dto.view;

import java.util.List;

import com.cakeshop.domain.product.entity.ProductType;

/** 관리자 상품 옵션 관리 화면 전체 조회 결과. */
public record ProductOptionManagementView(
        Long productId,
        String productName,
        ProductType productType,
        List<ProductOptionGroupAdminView> optionGroups
) {
}
