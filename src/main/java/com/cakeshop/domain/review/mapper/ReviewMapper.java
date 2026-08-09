package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.entity.Review;

@Mapper
public interface ReviewMapper {

    // 상태 조건을 붙이지 않는다. DELETED 후기도 행이 남아 uk_reviews_order_item 이 재작성을
    // 막으므로, 걸러 내지 않으면 목록에 띄워 놓고 저장에서 거절하게 된다 (R10).
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);

    int insert(Review review);
}
