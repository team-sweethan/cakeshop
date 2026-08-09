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

    /**
     * 관리자 답글 작성용.
     *
     * <p>{@code adminId}는 {@code members(id)} FK다. 사람이 다는 것을 전제하며 인증 관리자에서
     * 가져온다(docs/review/specs/review-reply.md C5).
     */
    public static ReviewReply create(Long reviewId, Long adminId, String content) {
        return new ReviewReply(null, reviewId, adminId, content);
    }
}
