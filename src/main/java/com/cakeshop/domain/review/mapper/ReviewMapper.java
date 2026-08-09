package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.entity.Review;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-09
 * 기능 : 후기 조회·저장
 * 설명 : reviews 외의 테이블을 JOIN 하지 않는다. 주문 상품 정보는 주문 도메인 계약이 돌려준다(DOMAIN.md 2.7).
 * ******************************
 */
@Mapper
public interface ReviewMapper {

    /** 작성 대상 목록에서 뺄 주문 상품 id. */
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    /** 상태를 보지 않는다 — 삭제된 후기도 행이 남아 재작성을 막는다(A1 / R10). */
    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);

    void insert(Review review);
}
