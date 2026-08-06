package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 자격 조회 계약
 * 설명 : 주문 상품 단건. 소유·상태 판단은 호출측이 한다. 조각 1(#109).
 * ******************************
 *
 * <p>주문 상태를 {@code OrderStatus} 그대로 노출하지 않고 {@code pickedUp} 하나로 줄인 것은,
 * 주문 도메인이 상태 어휘를 바꿔도 리뷰가 깨지지 않게 하려는 것이다. 리뷰가 필요한 판단은
 * "픽업이 끝났는가" 하나뿐이다.
 *
 * <p>상태와 무관하게 돌려주는 이유는 응답이 갈리기 때문이다 — 없는 주문 상품과 남의 주문
 * 상품은 404, 아직 픽업 전인 주문 상품은 400이다. 계약이 미리 걸러 버리면 리뷰가 둘을
 * 구분할 수 없다.
 */
public record OrderItemDetail(
        long orderItemId,
        long memberId,
        long productId,
        boolean pickedUp,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
