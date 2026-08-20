package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.query.AdminReviewFilter;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;

// MyBatis 가 이 인터페이스를 구현해 스프링 빈으로 올리고, 메서드 이름을
// src/main/resources/mapper/review/ReviewAdminMapper.xml 의 같은 id 와 짝지어 SQL 을 돌린다
// 고객용 ReviewMapper 와 갈라 둔 것은 관리자만 쓰는 조회·상태 변경을 한자리에 모으기 위해서다
@Mapper
public interface ReviewAdminMapper {

    // 검색 조건을 낱개로 늘어놓지 않고 AdminReviewFilter 객체 하나로 묶어 넘긴다
    //     XML 에서는 #{filter.writer}, #{filter.status} 처럼 점을 찍어 꺼낸다
    // 목록과 개수가 같은 filter 를 받아야 페이지 수가 화면과 어긋나지 않는다
    List<ReviewRow> findForAdmin(
            @Param("filter") AdminReviewFilter filter,
            @Param("offset") int offset,
            @Param("size") int size);

    long countForAdmin(@Param("filter") AdminReviewFilter filter);

    // expected 와 next 두 상태를 함께 넘긴다 -> UPDATE ... SET status = #{next} WHERE status = #{expected}
    // 반환 int 는 바뀐 행 수: 1 이면 성공, 0 이면 그사이 남이 먼저 상태를 바꾼 것이다
    //     읽어서 확인한 뒤 조건 없이 쓰는 것과 달리, 확인과 쓰기가 한 문장이라 그 사이가 없다
    int updateStatus(
            @Param("reviewId") long reviewId,
            @Param("expected") ReviewStatus expected,
            @Param("next") ReviewStatus next);

}
