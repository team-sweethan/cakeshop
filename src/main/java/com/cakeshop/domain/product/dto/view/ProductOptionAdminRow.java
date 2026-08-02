package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;

/** 관리자 옵션 조회 SQL의 평탄화된 한 행. */
public record ProductOptionAdminRow(
        Long groupId,
        String groupName,
        boolean required,
        ProductOptionSelectionType selectionType,
        ProductOptionStatus groupStatus,
        Integer groupSortOrder,
        Long optionId,
        String optionName,
        BigDecimal additionalPrice,
        ProductOptionStatus optionStatus,
        Integer optionSortOrder
) {
}
