package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성 자격 검증용 주문 상품 단건
 * 설명 : 후기 작성 폼과 등록이 자격을 검증할 때 쓴다. 주문이 상태 어휘를 바꿔도 리뷰가 깨지지 않도록
 *        OrderStatus 대신 pickedUp 으로 계산해 넘긴다 (MemberCommunityView 의 withdrawn 과 같은 판단).
 * ******************************
 */
public record OrderReviewTargetView(
        Long orderItemId,
        Long productId,
        String productName,
        String orderNumber,
        boolean pickedUp,
        LocalDateTime pickedUpAt
) {
}
