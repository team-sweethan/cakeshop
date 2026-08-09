package com.cakeshop.domain.product.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.domain.product.mapper.ProductReviewMapper;
import com.cakeshop.global.error.BusinessException;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-10
 * 기능 : 후기 집계를 상품 컬럼에 반영하는 쓰기 계약
 * 설명 : 리뷰가 계산한 평균·건수를 받아 products 에 쓴다. 상품은 받은 값을 검증하지 않는다 -
 *        reviews 를 읽는 것이 도메인 경계에 걸려서다. 근거는 docs/review/decisions/ADR-001.
 *        후기를 저장하기 전에 lockForRating 을 먼저 부른다.
 * ******************************
 */
@Service
public class ProductReviewCommandService {

    private final ProductMapper productMapper;
    private final ProductReviewMapper productReviewMapper;

    public ProductReviewCommandService(
            ProductMapper productMapper, ProductReviewMapper productReviewMapper) {
        this.productMapper = productMapper;
        this.productReviewMapper = productReviewMapper;
    }

    @Transactional
    public void lockForRating(long productId) {
        Product product = productMapper.findSalesInfoByIdForUpdate(productId);

        if (product == null) {
            throw new BusinessException(ProductErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void applyReviewAggregate(
            long productId, BigDecimal averageRating, long reviewCount) {

        int updatedRows =
                productReviewMapper.applyReviewAggregate(productId, averageRating, reviewCount);

        if (updatedRows == 0) {
            throw new BusinessException(ProductErrorCode.NOT_FOUND);
        }
    }
}
