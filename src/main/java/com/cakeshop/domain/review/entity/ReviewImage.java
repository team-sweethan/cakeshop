package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ReviewImage {

    @Setter
    private Long id;

    private final Long reviewId;
    private final String imageUrl;
    private final int sortOrder;

    private ReviewImage(Long id, Long reviewId, String imageUrl, int sortOrder) {
        this.id = id;
        this.reviewId = reviewId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public static ReviewImage create(Long reviewId, String imageUrl, int sortOrder) {
        return new ReviewImage(null, reviewId, imageUrl, sortOrder);
    }
}
