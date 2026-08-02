package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;

/**
 * 관리자 상품 목록의 한 행에 표시할 정보를 담는다.
 *
 * @param id 상품 식별자
 * @param name 상품명
 * @param productType 상품 유형
 * @param basePrice 기본 판매 가격
 * @param stockQuantity 현재 재고 수량
 * @param status 판매 상태
 * @param createdAt 상품 등록 일시
 */
public record ProductAdminListView(
        Long id,
        String name,
        ProductType productType,
        BigDecimal basePrice,
        Integer stockQuantity,
        ProductStatus status,
        LocalDateTime createdAt
) {

    /**
     * 재고 수량 제한이 없는 상품인지 확인한다.
     */
    public boolean isUnlimitedStock() {
        return stockQuantity == null;
    }

    /**
     * 품절 상품인지 확인한다.
     */
    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity == 0;
    }
}