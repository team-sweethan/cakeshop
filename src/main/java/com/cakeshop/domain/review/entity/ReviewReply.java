package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ReviewReply {

    @Setter
    private Long id;

    private final Long reviewId;
    private final Long adminId;
    private final String content;

    private ReviewReply(Long id, Long reviewId, Long adminId, String content) {
        this.id = id;
        this.reviewId = reviewId;
        this.adminId = adminId;
        this.content = content;
    }

    public static ReviewReply create(Long reviewId, Long adminId, String content) {
        return new ReviewReply(null, reviewId, adminId, content);
    }
}
