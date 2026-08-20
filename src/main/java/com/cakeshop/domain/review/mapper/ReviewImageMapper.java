package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;
import com.cakeshop.domain.review.entity.ReviewImage;

// MyBatis 가 이 인터페이스를 구현해 스프링 빈으로 올리고, 메서드 이름을
// src/main/resources/mapper/review/ReviewImageMapper.xml 의 같은 id 와 짝지어 SQL 을 돌린다
@Mapper
public interface ReviewImageMapper {

    // 후기 id 여러 개를 한 번에 넘기고 그 후기들의 이미지를 전부 받는다 (IN 절 한 방)
    //     findByReviewIds(List.of(37L, 38L)) -> WHERE review_id IN (37, 38)
    // 후기 목록 화면에서 한 건씩 이미지를 다시 물으면 쿼리가 건수만큼 늘어나므로 묶어서 받는다
    // 반환은 뒤섞인 한 덩어리다. 어느 후기 것인지는 Service 가 reviewId 로 갈라 담는다
    List<ReviewImageRow> findByReviewIds(@Param("reviewIds") List<Long> reviewIds);

    // 인자가 객체 하나뿐이라 @Param 이 없다 — XML 의 #{imageUrl} 이 ReviewImage 의 getter 를 찾아간다
    // 여러 장이면 이 메서드를 장수만큼 부른다
    int insert(ReviewImage image);

}
