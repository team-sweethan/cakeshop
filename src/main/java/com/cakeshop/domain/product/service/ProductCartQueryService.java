package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.view.ProductCartThumbnail;
import com.cakeshop.domain.product.mapper.ProductCartMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니 도메인에 상품 대표 이미지를 제공하는 공개 조회 계약이다. */
@Service
public class ProductCartQueryService {

    private final ProductCartMapper productCartMapper;

    public ProductCartQueryService(ProductCartMapper productCartMapper) {
        this.productCartMapper = productCartMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductCartThumbnail> getThumbnails(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productCartMapper.findThumbnailsByProductIds(productIds);
    }
}
