package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

public record MyReviewView(
        Long id,
        Long productId,
        String productName,
        String orderNumber,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt,
        ReviewStatus status
) {

    public static MyReviewView of(ReviewRow row, OrderReviewSnapshotView snapshot) {
        return new MyReviewView(
                row.id(),
                row.productId(),
                snapshot == null ? null : snapshot.productName(),
                snapshot == null ? null : snapshot.orderNumber(),
                row.overallRating(),
                row.tasteRating(),
                row.designRating(),
                row.serviceRating(),
                row.content(),
                row.createdAt(),
                row.status());
    }

    public boolean isBlocked() {
        return status == ReviewStatus.BLOCKED;
    }
}
