package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

// 관리자 후기 목록(templates/admin/review/list.html)의 한 줄
// 목록이라 별점은 총점만, 본문은 그대로 담고 이미지·답글은 담지 않는다 (상세에서 받는다)
// 삭제·숨김 후기도 목록에는 status 와 함께 나온다
public record AdminReviewListView(
        Long id,
        String authorName,
        String productName,
        Integer overallRating,
        String content,
        LocalDateTime createdAt,
        ReviewStatus status
) {

    // 후기 행 하나 + 회원 도메인이 준 작성자 + 주문 도메인이 준 상품 스냅샷을 합친다
    // 탈퇴 회원 문구는 ProductReviewView 의 상수를 가져다 써서 화면마다 달라지지 않게 한다
    public static AdminReviewListView from(
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
