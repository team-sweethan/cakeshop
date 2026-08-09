package com.cakeshop.domain.review.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.review.entity.ReviewStatus;

/**
 * 내가 쓴 후기 목록(B3)의 한 건.
 *
 * <p>작성자는 보는 사람 자신이라 표시명이 없다. 대신 어느 주문의 무엇에 쓴 후기인지 가리키는
 * 주문 스냅샷과, 숨겨졌는지를 알려 줄 상태가 들어간다(DOMAIN 2.6, spec B3).</p>
 */
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

    /**
     * 매퍼가 읽은 행에 주문 스냅샷을 붙인다.
     *
     * <p>{@code snapshot} 이 {@code null} 이면 이름 자리를 비운다. {@code reviews.order_item_id}
     * 는 NOT NULL FK 라 정상적으로는 오지 않지만, 여기서 예외를 던지면 후기 한 건 때문에 목록
     * 전체가 사라진다.</p>
     */
    public static MyReviewView of(ReviewRow row, OrderReviewSnapshotView snapshot) {
        return new MyReviewView(
                row.id(),
                // 주문 스냅샷이 아니라 후기가 가진 값을 쓴다. 후기 목록으로 가는 링크라 대상은
                // 그때 산 상품이 아니라 지금 후기가 붙어 있는 상품이어야 한다.
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

    /** 숨겨진 사실은 본인에게 알린다. 수정·삭제를 닫는 근거이기도 하다(DOMAIN 2.1). */
    public boolean isBlocked() {
        return status == ReviewStatus.BLOCKED;
    }
}
