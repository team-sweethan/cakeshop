package com.cakeshop.domain.product.mapper;

import com.cakeshop.domain.product.dto.view.ProductDetailView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {

    /**
     * 판매 중인 상품의 고객용 상세 정보를 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 판매 중인 상품 정보, 조건에 맞는 상품이 없으면 {@code null}
     */
    ProductDetailView findPublicDetailById(
            @Param("productId") long productId
    );
}
