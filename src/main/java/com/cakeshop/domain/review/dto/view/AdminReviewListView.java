package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

public record AdminReviewListView(
        Long id,
        String authorName,
        String productName,
        Integer overallRating,
        String content,
        LocalDateTime createdAt,
        ReviewStatus status
) {

    public static AdminReviewListView of(
            ReviewRow row, MemberReviewView author, OrderReviewSnapshotView snapshot) {

        return new AdminReviewListView(
                row.id(),
                author == null || author.withdrawn()
                        ? ProductReviewView.WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                snapshot == null ? null : snapshot.productName(),
                row.overallRating(),
                row.content(),
                row.createdAt(),
                row.status());
    }
}
