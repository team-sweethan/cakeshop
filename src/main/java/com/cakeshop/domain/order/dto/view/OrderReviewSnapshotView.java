package com.cakeshop.domain.order.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-10
 * 기능 : 이미 쓴 후기의 주문 상품 스냅샷
 * 설명 : 후기 목록이 order_items·orders 를 직접 JOIN 하지 않도록 상품명과 주문번호만 제공한다.
 * ******************************
 *
 * <p>{@link OrderReviewItemView} 와 나누어 두는 이유는 대상이 반대이기 때문이다 — 그쪽은
 * <b>아직 안 쓴</b> 주문 상품이고 여기는 <b>이미 쓴</b> 후기가 가리키는 주문 상품이다.</p>
 *
 * <p>상품명은 주문 시점 스냅샷({@code order_items.product_name})이다. 상품명이 나중에 바뀌어도
 * 고객이 그때 산 이름으로 보인다(docs/review/DOMAIN.md 2.6).</p>
 */
public record OrderReviewSnapshotView(
        Long orderItemId,
        String productName,
        String orderNumber
) {
}
