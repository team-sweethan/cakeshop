package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 폼의 주문 상품 표시
 * 설명 : 폼 상단에 보여 줄 주문 상품 정보. 조각 1(#109).
 * ******************************
 */
public record ReviewTargetView(
        long orderItemId,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
