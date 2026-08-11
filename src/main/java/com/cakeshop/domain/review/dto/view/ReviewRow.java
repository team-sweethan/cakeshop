package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

import com.cakeshop.domain.review.entity.ReviewStatus;

@Value
public class ReviewRow {

    Long id;
    Long orderItemId;
    Long productId;
    Long memberId;
    Integer overallRating;
    Integer tasteRating;
    Integer designRating;
    Integer serviceRating;
    String content;
    ReviewStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
