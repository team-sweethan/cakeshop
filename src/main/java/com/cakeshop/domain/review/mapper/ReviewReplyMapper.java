package com.cakeshop.domain.review.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.view.ReviewReplyView;
import com.cakeshop.domain.review.entity.ReviewReply;

@Mapper
public interface ReviewReplyMapper {

    // 후기 상태를 저장 문장 안에서 본다. 확인과 저장이 갈라져 있으면 답글 작성과 관리자
    // 숨김(C4)이 겹칠 때 먼저 커밋된 숨김을 못 보고 BLOCKED 후기에 답글이 남는다
    // (specs/review-reply.md C5).
    int insertForPublishedReview(ReviewReply reply);

    int updateContentForPublishedReview(
            @Param("reviewId") long reviewId, @Param("content") String content);

    ReviewReplyView findByReviewId(@Param("reviewId") long reviewId);

    List<ReviewReplyView> findByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);
}
