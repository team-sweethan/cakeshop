package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.review.dto.query.ReviewRow;

public record ProductReviewView(
        Long id,
        String authorName,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt,
        List<ReviewImageView> images,
        ReviewReplyView reply
) {

    public static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    public static ProductReviewView from(
            ReviewRow row,
            MemberReviewView author,
            List<ReviewImageView> images,
            ReviewReplyView reply) {

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
                images == null ? List.of() : List.copyOf(images),
                reply);
    }
}
