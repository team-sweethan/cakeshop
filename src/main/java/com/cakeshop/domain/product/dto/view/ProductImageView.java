package com.cakeshop.domain.product.dto.view;

/**
 * 고객 상품 화면에 표시할 상품 이미지.
 *
 * @param id 상품 이미지 식별자
 * @param imageUrl 이미지 웹 접근 경로
 * @param sortOrder 상품 안에서의 표시 순서
 */
public record ProductImageView(
        Long id,
        String imageUrl,
        Integer sortOrder
) {
}
