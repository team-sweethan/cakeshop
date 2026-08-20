package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.review.dto.query.ReviewReplyRow;

// 후기에 달린 사장님 답글. 후기당 최대 하나라 목록이 아니라 단건이다
// 답글이 없는 후기가 더 많아서, 이 자리는 화면에서 늘 null 일 수 있는 값으로 다룬다
public record ReviewReplyView(
        Long id,
        Long reviewId,
        String content,
        LocalDateTime createdAt
) {

    // row 가 null 이면 그대로 null 을 돌려준다 -> 부르는 쪽이 null 검사를 따로 하지 않아도 된다
    public static ReviewReplyView from(ReviewReplyRow row) {
        return row == null
                ? null
                : new ReviewReplyView(row.id(), row.reviewId(), row.content(), row.createdAt());
    }

}
