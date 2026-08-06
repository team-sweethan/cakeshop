package com.cakeshop.domain.review.mapper;

import com.cakeshop.domain.review.entity.Review;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 저장·조회
 * 설명 : reviews 테이블 접근을 담당한다. 조각 1(#109).
 * ******************************
 *
 * <p><b>이 매퍼는 {@code reviews}·{@code review_replies}·{@code review_images} 외의 테이블을
 * JOIN하지 않는다.</b> 주문·회원·상품 값이 필요하면 그 도메인의 공개 계약을 거친다
 * (SPEC 2.7). 예외는 없다.
 */
@Mapper
public interface ReviewMapper {

    /** 후기를 저장한다. {@code uk_reviews_order_item} 에 걸리면 예외가 난다. */
    int insertReview(@Param("review") Review review);

    /**
     * 그 회원이 이미 후기를 쓴 주문 상품 식별자를 모두 돌려준다.
     *
     * <p>작성할 후기 목록(A1)에서 주문 계약에 넘길 <b>제외 목록</b>이다.
     *
     * <p><b>상태를 보지 않는다.</b> {@code DELETED} 후기도 행이 남아 재작성을
     * {@code uk_reviews_order_item} 이 막으므로, 목록에 다시 띄워 놓고 저장에서 거절하면
     * 안 된다(PLAN R10).
     */
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    /** 그 주문 상품에 후기가 이미 있는지. 상태와 무관하다. */
    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);
}
