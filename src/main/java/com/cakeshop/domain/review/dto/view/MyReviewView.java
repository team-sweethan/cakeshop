package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

// 내 후기 목록·수정 화면(templates/customer/review/my.html, edit.html)의 후기 한 건
// ProductReviewView 와 달리 작성자 이름 대신 상품명·주문번호와 status 를 들고 있다
// 남의 후기는 여기 오지 않으므로 숨김(BLOCKED) 상태도 본인에게는 보여 준다
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
        ReviewStatus status,
        List<ReviewImageView> images,
        ReviewReplyView reply
) {

    // 정적 팩토리: 후기 행 + 주문 스냅샷 + 이미지 + 답글을 한 덩어리로 합친다
    // snapshot 이 null 이면 상품명·주문번호를 null 로 둔다 (주문 도메인 조회가 비어 온 경우)
    // 마지막 줄 삼항: status 가 BLOCKED 면 reply 자리에 null 을 넣는다
    //     화면에 th:if 를 따로 걸지 않아도 가려진 후기에는 답글이 실려 나가지 않는다
    public static MyReviewView from(
            ReviewRow row,
            OrderReviewSnapshotView snapshot,
            List<ReviewImageView> images,
            ReviewReplyView reply) {

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
                row.status(),
                images == null ? List.of() : List.copyOf(images),
                row.status() == ReviewStatus.BLOCKED ? null : reply);
    }

    // record 에도 메서드를 더할 수 있다. 화면에서 th:if="${review.blocked}" 로 쓰라고 둔 파생 값이다
    public boolean isBlocked() {
        return status == ReviewStatus.BLOCKED;
    }

}
