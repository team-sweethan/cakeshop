package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.review.entity.ReviewStatus;

/**
 * 매퍼가 {@code reviews} 한 행에서 읽어 오는 값.
 *
 * <p>작성자명과 상품명은 여기에 없다. 둘 다 다른 도메인 소유라 리뷰 SQL 이 JOIN 하지 못하고,
 * Service 가 계약으로 받아 화면용 View 를 조립한다(DOMAIN 2.7).</p>
 */
public record ReviewRow(
        Long id,
        Long orderItemId,
        Long productId,
        Long memberId,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        ReviewStatus status,
        LocalDateTime createdAt
) {
}
