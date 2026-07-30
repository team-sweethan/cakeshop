package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {

    private final ProductMapper productMapper;

    public ProductQueryService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 다른 도메인에 제공할 상품 판매 정보를 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 현재 상품 판매 정보
     * @throws BusinessException 상품이 없거나 판매 중이 아닌 경우
     */
    @Transactional(readOnly = true)
    public ProductSalesInfo getSalesInfo(long productId) {
        Product product =
                productMapper.findSalesInfoById(productId);

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(
                    ProductErrorCode.NOT_ON_SALE
            );
        }

        Integer stockQuantity = product.getStockQuantity();
        boolean available =
                stockQuantity == null || stockQuantity > 0;

        return new ProductSalesInfo(
                product.getId(),
                available,
                product.getBasePrice(),
                stockQuantity
        );
    }
}
