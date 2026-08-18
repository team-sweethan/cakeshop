package com.cakeshop.domain.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;
import com.cakeshop.domain.review.entity.ReviewImage;

@Mapper
public interface ReviewImageMapper {

    int insert(ReviewImage image);

    List<ReviewImageRow> findByReviewIds(@Param("reviewIds") List<Long> reviewIds);
}
