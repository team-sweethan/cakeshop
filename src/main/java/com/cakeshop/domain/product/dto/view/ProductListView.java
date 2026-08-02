package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductType;

/**
 * 고객 상품 목록 카드에 출력할 상품 정보를 담는다.
 *
 * @param id 상품 식별자
 * @param name 상품명
 * @param basePrice 기본 판매 가격
 * @param productType 상품 유형
 * @param stockQuantity 현재 재고 수량
 * @param averageRating 평균 평점
 * @param reviewCount 후기 개수
 * @param thumbnailUrl 대표 이미지 URL
 */
public record ProductListView(
        Long id,
        String name,
        BigDecimal basePrice,
        ProductType productType,
        Integer stockQuantity,
        BigDecimal averageRating,
        Integer reviewCount,
        String thumbnailUrl
) {

    /**
     * 재고 수량 제한 없이 주문할 수 있는 상품인지 확인한다.
     *
     * @return 재고 제한이 없으면 {@code true}
     */
    public boolean isUnlimitedStock() {
        return stockQuantity == null;
    }

    /**
     * 상품이 품절 상태인지 확인한다.
     *
     * @return 재고 수량이 0이면 {@code true}
     */
    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity == 0;
    }

    /**
     * 현재 상품을 주문할 수 있는지 확인한다.
     *
     * @return 재고 제한이 없거나 재고가 남아 있으면 {@code true}
     */
    public boolean isAvailable() {
        return stockQuantity == null || stockQuantity > 0;
    }

}
