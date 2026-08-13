package com.cakeshop.domain.cart.dto.view;

import java.util.List;

/**
 * cart 소유 데이터로 주문 도메인이 다건 일반 주문을 구성할 때 사용하는 최소 조회 계약이다.
 * 상품·옵션의 현재 판매 가능 여부와 금액은 주문 도메인에서 다시 검증한다.
 */
public record CartOrderItemView(
        long cartItemId,
        long productId,
        int quantity,
        String requirements,
        List<Long> optionIds
) {
}
