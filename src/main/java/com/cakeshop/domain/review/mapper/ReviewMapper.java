package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.command.ReviewUpdateCommand;
import com.cakeshop.domain.review.dto.query.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.Review;

@Mapper
public interface ReviewMapper {

    // 상태 조건을 붙이지 않는다. DELETED 후기도 행이 남아 uk_reviews_order_item 이 재작성을
    // 막으므로, 걸러 내지 않으면 목록에 띄워 놓고 저장에서 거절하게 된다 (R10).
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);

    int insert(Review review);

    ReviewRow findById(@Param("id") long id);

    // 조건부 쓰기가 0행일 때 원인을 가리는 자리에만 쓴다. 일반 SELECT 는 자격 검증 시점의
    // 스냅샷을 보므로 그사이 커밋된 삭제·숨김이 안 보이고 원인이 400 으로 뭉개진다.
    ReviewRow findByIdForUpdate(@Param("id") long id);

    int update(ReviewUpdateCommand command);

    int deleteByAuthor(@Param("reviewId") long reviewId, @Param("memberId") long memberId);

    // FOR UPDATE 를 빼면 REPEATABLE READ 스냅샷이 자격 검증 시점에 굳어 먼저 커밋된 후기를
    // 못 본다 (D1).
    ProductRatingAggregate aggregateForUpdate(@Param("productId") long productId);

    List<ReviewRow> findPublishedByProductId(
            @Param("productId") long productId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countPublishedByProductId(@Param("productId") long productId);

    List<ReviewRow> findByMemberId(
            @Param("memberId") long memberId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countByMemberId(@Param("memberId") long memberId);
}
