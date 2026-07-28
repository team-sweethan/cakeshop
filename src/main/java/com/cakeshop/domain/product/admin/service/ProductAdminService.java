package com.cakeshop.domain.product.admin.service;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import com.cakeshop.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAdminService {

    private final ProductMapper productMapper;

    public ProductAdminService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 검색 조건에 맞는 관리자 상품 목록을 조회한다.
     *
     * @param condition 상품 검색 및 필터 조건
     * @param pageRequest 페이지 요청 정보
     * @return 페이징된 관리자 상품 목록
     */
    @Transactional(readOnly = true)
    public PageResult<ProductAdminListView> getProducts(
            ProductAdminSearchCondition condition,
            PageRequest pageRequest
    ) {
        // 검색 조건이 없으면 비어 있는 검색 조건을 사용한다.
        ProductAdminSearchCondition normalizedCondition =
                condition == null
                        ? new ProductAdminSearchCondition()
                        : condition;

        // 검색어의 앞뒤 공백을 제거하고 빈 검색어는 null로 변경한다.
        normalizedCondition.setKeyword(
                normalizedCondition.normalizedKeyword()
        );

        // 페이지 요청이 없으면 PageRequest의 기본 페이지와 크기를 사용한다.
        PageRequest normalizedPageRequest =
                pageRequest == null
                        ? new PageRequest(null, null)
                        : pageRequest;

        // 현재 검색 조건에 맞는 전체 상품 개수를 조회한다.
        long totalElements =
                productMapper.countAdminProducts(
                        normalizedCondition
                );

        List<ProductAdminListView> products;

        // 검색 결과가 없으면 목록 쿼리를 실행하지 않고 빈 목록을 사용한다.
        if (totalElements == 0) {
            products = List.of();
        } else {
            // 검색 조건과 페이지 정보에 맞는 상품 목록을 조회한다.
            products = productMapper.findAdminProducts(
                    normalizedCondition,
                    normalizedPageRequest.getSize(),
                    normalizedPageRequest.getOffset()
            );
        }

        // 상품 목록과 페이지 정보를 하나의 결과로 조립한다.
        return new PageResult<>(
                products,
                normalizedPageRequest,
                totalElements
        );
    }

    /**
     * 상품의 판매 상태를 변경한다.
     *
     * @param productId 상태를 변경할 상품 식별자
     * @param status 변경할 판매 상태
     * @throws BusinessException 존재하지 않는 상품인 경우
     */
    @Transactional
    public void changeProductStatus(
            long productId,
            ProductStatus status
    ) {
        // 상품의 판매 상태와 수정 일시를 변경한다.
        int updatedRows =
                productMapper.updateProductStatus(
                        productId,
                        status
                );

        // 변경된 행이 없으면 존재하지 않는 상품으로 처리한다.
        if (updatedRows == 0) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }
    }

    /**
     * 상품 등록 화면에서 선택할 활성 카테고리를 조회한다.
     *
     * @return 활성 카테고리 목록
     */
    @Transactional(readOnly = true)
    public List<ProductCategoryOptionView> getActiveCategories() {
        // 화면에 표시할 수 있는 활성 카테고리만 조회한다.
        return productMapper.findActiveCategories();
    }

    /**
     * 새로운 상품의 기본 정보를 등록한다.
     *
     * <p>등록 직후에는 고객 화면에 노출되지 않도록
     * 판매 중지 상태로 저장한다.</p>
     *
     * @param form 상품 등록 입력값
     * @return 생성된 상품 식별자
     * @throws BusinessException 선택할 수 없는 카테고리인 경우
     */
    @Transactional
    public long createProduct(ProductForm form) {
        // 카테고리 ID가 없거나 활성 카테고리가 아니면 등록을 중단한다.
        if (form == null
                || form.getCategoryId() == null
                || !productMapper.existsActiveCategoryById(
                form.getCategoryId()
        )) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_CATEGORY
            );
        }

        // 검증된 등록 폼을 DB에 저장할 Product 객체로 변환한다.
        Product product = new Product();

        product.setCategoryId(form.getCategoryId());
        product.setName(form.normalizedName());
        product.setDescription(
                form.normalizedDescription()
        );
        product.setBasePrice(form.getBasePrice());
        product.setStockQuantity(
                form.getStockQuantity()
        );
        product.setProductType(form.getProductType());
        product.setPreparationDays(
                form.getPreparationDays()
        );
        product.setCancellationLimitDays(
                form.getCancellationLimitDays()
        );

        // 옵션과 내용을 확인한 후 판매를 시작할 수 있도록 기본 상태를 판매 중지로 설정한다.
        product.setStatus(ProductStatus.INACTIVE);

        // 상품을 등록하고 자동 생성된 상품 ID를 Product에 저장한다.
        productMapper.insertProduct(product);

        return product.getId();
    }
}