package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

@Value
public class AdminReviewDetailView {

    Long id;
    Long productId;
    String authorName;
    String productName;
    String orderNumber;
    Integer overallRating;
    Integer tasteRating;
    Integer designRating;
    Integer serviceRating;
    String content;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    ReviewStatus status;
    ReviewReplyView reply;

    public static AdminReviewDetailView from(
            ReviewRow row,
            MemberReviewView author,
            OrderReviewSnapshotView snapshot,
            ReviewReplyView reply) {

        return new AdminReviewDetailView(
                row.getId(),
                row.getProductId(),
                author == null || author.withdrawn()
                        ? ProductReviewView.WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                snapshot == null ? null : snapshot.productName(),
                snapshot == null ? null : snapshot.orderNumber(),
                row.getOverallRating(),
                row.getTasteRating(),
                row.getDesignRating(),
                row.getServiceRating(),
                row.getContent(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getStatus(),
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
