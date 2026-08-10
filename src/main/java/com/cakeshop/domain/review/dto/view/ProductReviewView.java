package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.dto.view.MemberReviewView;

public record ProductReviewView(
        Long id,
        String authorName,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt,
        ReviewReplyView reply
) {

    public static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    public static ProductReviewView of(
            ReviewRow row, MemberReviewView author, ReviewReplyView reply) {

        return new ProductReviewView(
                row.id(),
                author == null || author.withdrawn()
                        ? WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                row.overallRating(),
                row.tasteRating(),
                row.designRating(),
                row.serviceRating(),
                row.content(),
                row.createdAt(),
                reply);
    }
}
