package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

public record ReviewReplyView(
        Long id,
        Long reviewId,
        String content,
        LocalDateTime createdAt
) {
}
