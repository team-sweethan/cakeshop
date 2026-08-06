package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 작성할 후기 목록 표시
 * 설명 : 아직 후기를 쓰지 않은 주문 상품 한 줄. 조각 1(#109).
 * ******************************
 */
public record WritableReviewView(
        long orderItemId,
        String productName,
        String orderNumber,
        LocalDateTime pickedUpAt
) {
}
