package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

@Value
public class ReviewReplyView {

    Long id;
    Long reviewId;
    String content;
    LocalDateTime createdAt;
}
