package com.cakeshop.domain.review.dto.command;

public record ReviewUpdateCommand(
        Long id,
        Long memberId,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content
) {
}
