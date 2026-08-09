package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.view.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.Review;

@Mapper
public interface ReviewMapper {

    // 상태 조건을 붙이지 않는다. DELETED 후기도 행이 남아 uk_reviews_order_item 이 재작성을
    // 막으므로, 걸러 내지 않으면 목록에 띄워 놓고 저장에서 거절하게 된다 (R10).
    List<Long> findReviewedOrderItemIds(@Param("memberId") long memberId);

    boolean existsByOrderItemId(@Param("orderItemId") long orderItemId);

    int insert(Review review);

    // FOR UPDATE 를 빼면 REPEATABLE READ 스냅샷이 자격 검증 시점에 굳어 먼저 커밋된 후기를
    // 못 본다 (D1).
    ProductRatingAggregate aggregateForUpdate(@Param("productId") long productId);

    // 미리보기 3개와 전체 목록이 같은 조회를 쓴다. 정렬이 갈리면 상세에 보이던 3개와 전체 목록
    // 첫 3개가 달라진다 (B1).
    List<ReviewRow> findPublishedByProductId(
            @Param("productId") long productId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countPublishedByProductId(@Param("productId") long productId);

    // BLOCKED 는 본인에게만 보인다. 숨겨진 사실을 본인이 알아야 하기 때문이다 (B3).
    List<ReviewRow> findByMemberId(
            @Param("memberId") long memberId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countByMemberId(@Param("memberId") long memberId);
}
