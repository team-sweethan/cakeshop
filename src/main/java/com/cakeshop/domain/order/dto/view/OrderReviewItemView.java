package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성 대상 주문 상품 목록 항목
 * 설명 : 리뷰가 order_items·orders 를 직접 JOIN 하지 않도록 목록 표시에 필요한 최소 필드만 제공한다.
 * ******************************
 */
public record OrderReviewItemView(
        Long orderItemId,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
