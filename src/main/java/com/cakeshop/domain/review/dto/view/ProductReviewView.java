package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

import com.cakeshop.domain.member.dto.view.MemberReviewView;

@Value
public class ProductReviewView {

    public static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    Long id;
    String authorName;
    Integer overallRating;
    Integer tasteRating;
    Integer designRating;
    Integer serviceRating;
    String content;
    LocalDateTime createdAt;
    ReviewReplyView reply;

    public static ProductReviewView from(
            ReviewRow row, MemberReviewView author, ReviewReplyView reply) {

        return new ProductReviewView(
                row.getId(),
                author == null || author.withdrawn()
                        ? WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                row.getOverallRating(),
                row.getTasteRating(),
                row.getDesignRating(),
                row.getServiceRating(),
                row.getContent(),
                row.getCreatedAt(),
                reply);
    }
}
