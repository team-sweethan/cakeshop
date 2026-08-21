package com.cakeshop.domain.product.dto.view;

/**
 * 장바구니 화면에 표시할 상품 대표 이미지 계약이다.
 *
 * @param productId 상품 식별자
 * @param thumbnailUrl 대표 이미지 URL. 등록된 이미지가 없으면 {@code null}
 */
public record ProductCartThumbnail(
        long productId,
        String thumbnailUrl
) {
}
