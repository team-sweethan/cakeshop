package com.cakeshop.domain.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

@Service
public class ProductService {

    /** 상품 데이터 조회를 담당하는 Mapper. */
    private final ProductMapper productMapper;

    /**
     * ProductService가 사용할 Mapper를 주입받는다.
     *
     * @param productMapper 상품 조회 Mapper
     */
    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 고객 상품 상세 화면에 표시할 판매 중인 상품을 조회한다.
     *
     * <p>존재하지 않거나 판매 중지된 상품은 고객에게 공개하지 않고
     * 상품을 찾을 수 없다는 예외를 발생시킨다.</p>
     *
     * @param productId 조회할 상품 식별자
     * @return 고객 상품 상세 정보
     * @throws BusinessException 상품을 조회할 수 없는 경우
     */
    @Transactional(readOnly = true)
    public ProductDetailView getPublicDetail(long productId) {
        ProductDetailView product =
                productMapper.findPublicDetailById(productId);

        if (product == null) {
            throw new BusinessException(ProductErrorCode.NOT_FOUND);
        }

        return product;
    }
}