package com.cakeshop.domain.review.dto.query;

import java.time.LocalDateTime;

public record ReviewReplyRow(
        Long id,
        Long reviewId,
        String content,
        LocalDateTime createdAt
) {
}
