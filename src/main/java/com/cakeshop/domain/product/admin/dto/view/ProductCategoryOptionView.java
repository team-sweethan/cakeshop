package com.cakeshop.domain.product.admin.dto.view;

/**
 * 관리자 상품 등록 화면의 카테고리 선택지를 담는다.
 *
 * @param id 카테고리 식별자
 * @param name 화면에 표시할 카테고리 이름
 */
public record ProductCategoryOptionView(
        Long id,
        String name
) {
}