package com.cakeshop.domain.order.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-10
 * 기능 : 이미 쓴 후기의 주문 상품 스냅샷
 * 설명 : 후기 목록이 order_items·orders 를 직접 JOIN 하지 않도록 상품명과 주문번호만 제공한다.
 * ******************************
 */
public record OrderReviewSnapshotView(
        Long orderItemId,
        String productName,
        String orderNumber
) {
}
