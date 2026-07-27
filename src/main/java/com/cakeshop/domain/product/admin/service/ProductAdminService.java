package com.cakeshop.domain.product.admin.service;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAdminService {

    private final ProductMapper productMapper;

    public ProductAdminService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 관리자 상품 목록에 표시할 전체 상품을 조회한다.
     *
     * @param pageRequest 페이지 요청 정보
     * @return 페이징된 관리자 상품 목록
     */
    @Transactional(readOnly = true)
    public PageResult<ProductAdminListView> getProducts(
            PageRequest pageRequest
    ) {
        // 페이지 요청이 없으면 PageRequest에 정의된 기본 페이지와 크기를 사용한다.
        PageRequest normalizedPageRequest =
                pageRequest == null
                        ? new PageRequest(null, null)
                        : pageRequest;

        // 페이징에 필요한 전체 상품 개수를 조회한다.
        long totalElements =
                productMapper.countAdminProducts();

        List<ProductAdminListView> products;

        // 등록된 상품이 없으면 목록 조회 쿼리를 실행하지 않고 빈 목록을 사용한다.
        if (totalElements == 0) {
            products = List.of();
        } else {
            // 현재 페이지에 표시할 상품만 조회한다.
            products = productMapper.findAdminProducts(
                    normalizedPageRequest.getSize(),
                    normalizedPageRequest.getOffset()
            );
        }

        // 상품 목록, 현재 페이지 정보, 전체 상품 개수를 하나의 결과로 조립한다.
        return new PageResult<>(
                products,
                normalizedPageRequest,
                totalElements
        );
    }
}