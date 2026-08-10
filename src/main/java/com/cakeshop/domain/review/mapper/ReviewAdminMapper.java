package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.view.AdminReviewFilter;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

@Mapper
public interface ReviewAdminMapper {

    List<ReviewRow> findForAdmin(
            @Param("filter") AdminReviewFilter filter,
            @Param("offset") int offset,
            @Param("size") int size);

    long countForAdmin(@Param("filter") AdminReviewFilter filter);

    // 기대 상태를 쓰기 조건에 함께 넣는다. 읽고 나서 조건 없이 쓰면 그사이 작성자 삭제(A5)와
    // 관리자 숨김(C4)이 둘 다 통과하고 나중 쓰기가 앞의 조치를 덮는다 (DOMAIN.md 2.1).
    int updateStatus(
            @Param("reviewId") long reviewId,
            @Param("expected") ReviewStatus expected,
            @Param("next") ReviewStatus next);
}
