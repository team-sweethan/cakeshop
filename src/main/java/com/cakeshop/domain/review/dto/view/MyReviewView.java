package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import lombok.Value;

import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

@Value
public class MyReviewView {

    Long id;
    Long productId;
    String productName;
    String orderNumber;
    Integer overallRating;
    Integer tasteRating;
    Integer designRating;
    Integer serviceRating;
    String content;
    LocalDateTime createdAt;
    ReviewStatus status;
    ReviewReplyView reply;

    // 숨겨진 후기의 답글은 여기서 떨어뜨린다. 화면마다 조건을 적으면 한 곳을 빠뜨렸을 때
    // 가려진 후기에 사장님 답글만 남는다 (specs/review-reply.md B4).
    public static MyReviewView from(
            ReviewRow row, OrderReviewSnapshotView snapshot, ReviewReplyView reply) {

        return new MyReviewView(
                row.getId(),
                row.getProductId(),
                snapshot == null ? null : snapshot.productName(),
                snapshot == null ? null : snapshot.orderNumber(),
                row.getOverallRating(),
                row.getTasteRating(),
                row.getDesignRating(),
                row.getServiceRating(),
                row.getContent(),
                row.getCreatedAt(),
                row.getStatus(),
                row.getStatus() == ReviewStatus.BLOCKED ? null : reply);
    }

    public boolean isBlocked() {
        return status == ReviewStatus.BLOCKED;
    }
}
