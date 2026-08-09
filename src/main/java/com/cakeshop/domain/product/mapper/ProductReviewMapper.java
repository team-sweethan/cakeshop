package com.cakeshop.domain.product.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-10
 * 기능 : 후기 집계를 상품 컬럼에 반영하는 SQL
 * 설명 : products 만 만진다. 잠금 조회는 기존 ProductMapper.findSalesInfoByIdForUpdate 를 재사용하므로
 *        여기에 복제하지 않는다. 근거는 docs/review/specs/product-rating.md.
 * ******************************
 */
@Mapper
public interface ProductReviewMapper {

    int applyReviewAggregate(
            @Param("productId") long productId,
            @Param("averageRating") BigDecimal averageRating,
            @Param("reviewCount") long reviewCount);
}
