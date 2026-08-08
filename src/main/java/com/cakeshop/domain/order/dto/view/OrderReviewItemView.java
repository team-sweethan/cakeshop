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
 *
 * <p>화면(`작성할 후기 목록`)이 상품명·주문번호·픽업 일시만 쓰므로 그 셋과 다음 화면으로 넘길
 * {@code orderItemId} 만 담는다. 금액·수량·옵션은 후기와 무관하다.</p>
 *
 * <p><b>수량은 담지 않는다.</b> {@code order_items.quantity} 가 2 이상이어도 후기는 한 건이다 —
 * 후기의 단위는 수량이 아니라 주문 상품 행이다({@code specs/review-write.md} A1).</p>
 */
public record OrderReviewItemView(
        Long orderItemId,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
