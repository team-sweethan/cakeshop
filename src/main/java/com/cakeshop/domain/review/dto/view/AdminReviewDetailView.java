package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

// 관리자 후기 상세(templates/admin/review/detail.html) 한 건
// 목록용 AdminReviewListView 에 세부 별점·주문번호·수정시각·이미지·답글까지 더한 판이다
// 아래 세 boolean 메서드로 화면이 숨김/노출 버튼 중 어느 쪽을 그릴지 고른다
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

    // MyReviewView.from 과 달리 답글을 상태로 떨어뜨리지 않는다 — 관리자는 숨긴 후기의 답글도 봐야 한다
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

    // 화면에서는 review.published / review.blocked / review.deleted 로 읽힌다
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
