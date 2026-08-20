package com.cakeshop.domain.review.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.query.ReviewReplyRow;
import com.cakeshop.domain.review.entity.ReviewReply;

// MyBatis 가 이 인터페이스를 구현해 스프링 빈으로 올리고, 메서드 이름을
// src/main/resources/mapper/review/ReviewReplyMapper.xml 의 같은 id 와 짝지어 SQL 을 돌린다
// 메서드 이름 뒤의 ForPublishedReview 는 "노출 중인 후기일 때만"이라는 조건이 SQL 안에 들어 있다는 뜻이다
@Mapper
public interface ReviewReplyMapper {

    // 답글은 후기당 하나라 단건이다. 없으면 null 이 온다 (Optional 이 아니다)
    ReviewReplyRow findByReviewId(@Param("reviewId") long reviewId);

    // 후기 id 여러 개를 한 번에 넘기고 그 후기들의 답글을 한 덩어리로 받는다
    //     findByReviewIds(List.of(37L, 38L)) -> WHERE review_id IN (37, 38)
    // 답글이 없는 후기는 결과에 아예 빠진다. 목록 크기가 넘긴 id 수와 다를 수 있다
    // 매개변수가 Collection 이라 List 든 Set 이든 그대로 넘길 수 있다
    List<ReviewReplyRow> findByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    // 후기 상태 검사가 자바가 아니라 INSERT 문 안에 들어 있다
    //     INSERT ... SELECT ... WHERE r.status = 'PUBLISHED' 모양이라 조건이 안 맞으면 아무 행도 안 들어간다
    // 반환 int 는 실제로 들어간 행 수다. 후기가 노출 중이 아니면 0 이 오고 답글은 남지 않는다
    int insertForPublishedReview(ReviewReply reply);

    // 위와 같은 이유로 수정도 후기 상태를 조건에 넣는다. 0 이면 그사이 숨겨졌거나 삭제된 것이다
    int updateContentForPublishedReview(
            @Param("reviewId") long reviewId, @Param("content") String content);

}
