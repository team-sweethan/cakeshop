package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

public record AdminReviewDetailView(
        Long id,
        Long productId,
        String authorName,
        String productName,
        String orderNumber,
        Integer overallRating,
        Integer tasteRating,
        Integer designRating,
        Integer serviceRating,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ReviewStatus status,
        List<ReviewImageView> images,
        ReviewReplyView reply
) {

    public static AdminReviewDetailView from(
            ReviewRow row,
            MemberReviewView author,
            OrderReviewSnapshotView snapshot,
            List<ReviewImageView> images,
            ReviewReplyView reply) {

        return new AdminReviewDetailView(
                row.id(),
                row.productId(),
                author == null || author.withdrawn()
                        ? ProductReviewView.WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                snapshot == null ? null : snapshot.productName(),
                snapshot == null ? null : snapshot.orderNumber(),
                row.overallRating(),
                row.tasteRating(),
                row.designRating(),
                row.serviceRating(),
                row.content(),
                row.createdAt(),
                row.updatedAt(),
                row.status(),
                images == null ? List.of() : List.copyOf(images),
                reply);
    }

    public boolean isPublished() {
        return status == ReviewStatus.PUBLISHED;
    }

    public boolean isBlocked() {
        return status == ReviewStatus.BLOCKED;
    }

    public boolean isDeleted() {
        return status == ReviewStatus.DELETED;
    }
}
