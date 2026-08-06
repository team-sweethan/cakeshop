package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 도메인 모델
 * 설명 : ReviewReply 도메인의 상태와 값을 정의한다.
 * ******************************
 */
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
     * 가져온다(docs/review/SPEC.md C5).
     */
    public static ReviewReply create(Long reviewId, Long adminId, String content) {
        return new ReviewReply(null, reviewId, adminId, content);
    }
}
