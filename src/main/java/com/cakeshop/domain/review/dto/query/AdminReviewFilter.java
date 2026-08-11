package com.cakeshop.domain.review.dto.query;

import java.util.List;

import com.cakeshop.domain.review.entity.ReviewStatus;

/**
 * 관리자 후기 검색 조건. {@code null} 목록은 조건 없음, 빈 목록은 일치 결과 없음이다.
 */
public record AdminReviewFilter(
        List<Long> memberIds,
        List<Long> orderItemIds,
        Integer minimumRating,
        Integer maximumRating,
        ReviewStatus status
) {
}
