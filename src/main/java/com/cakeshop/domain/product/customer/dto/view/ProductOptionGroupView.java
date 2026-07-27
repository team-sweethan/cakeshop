package com.cakeshop.domain.product.customer.dto.view;

import java.util.List;

/**
 * 고객 상품 상세 화면에 표시할 옵션 그룹.
 *
 * @param id 옵션 그룹 식별자
 * @param name 옵션 그룹 이름
 * @param required 필수 선택 여부
 * @param selectionType 단일 또는 복수 선택 방식
 * @param options 그룹에 포함된 판매 중인 옵션 목록
 */
public record ProductOptionGroupView(
        Long id,
        String name,
        boolean required,
        String selectionType,
        List<ProductOptionItemView> options
) {
}
