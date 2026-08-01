package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

/**
 * 상품 옵션 조회 SQL의 한 행을 표현한다.
 *
 * <p>Service가 같은 그룹의 행을 묶어
 * {@link ProductOptionGroupView}로 변환한다.</p>
 */
public record ProductOptionRow(
        Long groupId,
        String groupName,
        boolean required,
        String selectionType,
        Long optionId,
        String optionName,
        BigDecimal additionalPrice
) {
}
