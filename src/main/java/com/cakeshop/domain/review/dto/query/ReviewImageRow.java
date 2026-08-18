package com.cakeshop.domain.review.dto.query;

public record ReviewImageRow(
        Long id,
        Long reviewId,
        String imageUrl,
        int sortOrder
) {
}
