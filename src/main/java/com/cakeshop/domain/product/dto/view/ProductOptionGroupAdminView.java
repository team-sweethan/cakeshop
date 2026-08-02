package com.cakeshop.domain.product.dto.view;

import java.util.List;

import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;

/** 관리자 화면에 표시할 옵션 그룹과 하위 옵션. */
public record ProductOptionGroupAdminView(
        Long id,
        String name,
        boolean required,
        ProductOptionSelectionType selectionType,
        ProductOptionStatus status,
        Integer sortOrder,
        List<ProductOptionAdminView> options
) {
}
