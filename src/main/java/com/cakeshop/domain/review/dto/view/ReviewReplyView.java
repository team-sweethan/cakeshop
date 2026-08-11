package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.review.dto.query.ReviewReplyRow;

public record ReviewReplyView(
        Long id,
        Long reviewId,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewReplyView from(ReviewReplyRow row) {
        return row == null
                ? null
                : new ReviewReplyView(row.id(), row.reviewId(), row.content(), row.createdAt());
    }
}
