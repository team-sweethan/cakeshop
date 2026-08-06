package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 자격 조회 계약
 * 설명 : 픽업 완료된 주문 상품 한 건. 리뷰 도메인이 작성할 후기 목록에 쓴다. 조각 1(#109).
 * ******************************
 */
public record PickedUpOrderItem(
        long orderItemId,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
