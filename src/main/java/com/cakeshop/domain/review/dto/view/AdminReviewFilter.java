package com.cakeshop.domain.review.dto.view;

import java.util.List;

import com.cakeshop.domain.review.entity.ReviewStatus;

/**
 * 관리자 후기 검색 조건.
 *
 * <p>{@code memberIds}·{@code orderItemIds} 는 <b>{@code null} 과 빈 목록의 뜻이 다르다.</b>
 * {@code null} 은 그 조건을 걸지 않는 것이고, 빈 목록은 계약이 "일치하는 것이 없다"고 답한
 * 것이라 결과가 0건이어야 한다.</p>
 */
public record AdminReviewFilter(
        List<Long> memberIds,
        List<Long> orderItemIds,
        AdminReviewRating rating,
        ReviewStatus status
) {

    public AdminReviewFilter {
        rating = rating == null ? AdminReviewRating.ALL : rating;
    }
}
