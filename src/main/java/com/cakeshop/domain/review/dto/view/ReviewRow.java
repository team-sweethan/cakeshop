package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.review.entity.ReviewStatus;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
