package com.cakeshop.domain.review.dto.view;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;

public record ReviewImageView(
        Long id,
        String imageUrl,
        int sortOrder
) {

    public static ReviewImageView from(ReviewImageRow row) {
        return new ReviewImageView(row.id(), row.imageUrl(), row.sortOrder());
    }
}
