package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 평점 집계
 * 설명 : 한 상품의 공개 후기 평균 평점과 건수. 조각 2(#33).
 * ******************************
 */
public record ProductRatingSummary(
        BigDecimal averageRating,
        long reviewCount
) {
}
