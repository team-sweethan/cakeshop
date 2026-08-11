package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

@Value
public class AdminReviewListView {

    Long id;
    String authorName;
    String productName;
    Integer overallRating;
    String content;
    LocalDateTime createdAt;
    ReviewStatus status;

    public static AdminReviewListView from(
            ReviewRow row, MemberReviewView author, OrderReviewSnapshotView snapshot) {

        return new AdminReviewListView(
                row.getId(),
                author == null || author.withdrawn()
                        ? ProductReviewView.WITHDRAWN_AUTHOR_NAME
                        : author.nickname(),
                snapshot == null ? null : snapshot.productName(),
                row.getOverallRating(),
                row.getContent(),
                row.getCreatedAt(),
                row.getStatus());
    }
}
