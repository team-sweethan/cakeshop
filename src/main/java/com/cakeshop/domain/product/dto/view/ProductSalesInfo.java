package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

/**
 * 다른 도메인에 제공하는 상품 판매 정보.
 *
 * <p>장바구니와 주문 도메인이 상품 Entity나 Mapper를 직접 참조하지 않고
 * 현재 판매 가격과 재고 정보를 확인할 때 사용한다.</p>
 *
 * @param productId 상품 식별자
 * @param available 현재 재고를 기준으로 주문할 수 있는지 여부
 * @param basePrice 옵션 추가 금액을 제외한 기본 판매 가격
 * @param stockQuantity 현재 재고 수량, {@code null}이면 재고 제한 없음
 */
public record ProductSalesInfo(
        long productId,
        boolean available,
        BigDecimal basePrice,
        Integer stockQuantity
) {
}
