package com.cakeshop.domain.product.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductImageView;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionItemView;
import com.cakeshop.domain.product.dto.view.ProductOptionRow;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final BigDecimal DEFAULT_MIN_PRICE =
            BigDecimal.ZERO;

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
     * 고객 상품 목록에 표시할 판매 중인 상품을 조회한다.
     *
     * @param condition 상품 검색·필터·정렬 조건
     * @param pageRequest 페이지 요청 정보
     * @return 페이징된 고객 상품 목록
     */
    @Transactional(readOnly = true)
    public PageResult<ProductListView> getPublicProducts(
            ProductSearchCondition condition,
            PageRequest pageRequest
    ) {
        // 검색 조건이 없거나 값이 잘못된 경우 기본 검색 조건으로 정리한다.
        ProductSearchCondition normalizedCondition =
                normalizeCondition(condition);

        // 페이지 요청이 없으면 PageRequest의 기본값인 1페이지, 20개를 사용한다.
        PageRequest normalizedPageRequest =
                pageRequest == null
                        ? new PageRequest(null, null)
                        : pageRequest;

        // 현재 검색 조건에 맞는 전체 상품 개수를 먼저 조회한다.
        long totalElements =
                productMapper.countPublicProducts(normalizedCondition);

        List<ProductListView> products;

        // 검색 결과가 없으면 목록 쿼리를 실행하지 않고 빈 목록을 사용한다.
        if (totalElements == 0) {
            products = List.of();
        } else {
            // 검색 결과가 있으면 현재 페이지에 필요한 상품만 조회한다.
            products = productMapper.findPublicProducts(
                    normalizedCondition,
                    normalizedPageRequest.getSize(),
                    normalizedPageRequest.getOffset()
            );
        }

        // 상품 목록과 페이지 정보, 전체 개수를 하나의 페이지 결과로 조립한다.
        return new PageResult<>(
                products,
                normalizedPageRequest,
                totalElements
        );
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

        List<ProductImageView> images =
                productMapper.findProductImagesByProductId(
                        productId
                ).stream()
                        .map(this::toProductImageView)
                        .toList();

        product.setImages(images);

        return product;
    }

    private ProductImageView toProductImageView(
            ProductImage image
    ) {
        return new ProductImageView(
                image.getId(),
                image.getImageUrl(),
                image.getSortOrder()
        );
    }

    /**
     * 고객 상품 상세 화면에 표시할 활성 옵션을 그룹 단위로 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 옵션 그룹 목록
     */
    @Transactional(readOnly = true)
    public List<ProductOptionGroupView> getPublicOptionGroups(
            long productId
    ) {
        List<ProductOptionRow> rows =
                productMapper.findPublicOptionRowsByProductId(
                        productId
                );

        Map<Long, List<ProductOptionRow>> rowsByGroup =
                new LinkedHashMap<>();

        for (ProductOptionRow row : rows) {
            rowsByGroup
                    .computeIfAbsent(
                            row.groupId(),
                            ignored -> new java.util.ArrayList<>()
                    )
                    .add(row);
        }

        return rowsByGroup.values().stream()
                .map(groupRows -> {
                    ProductOptionRow first = groupRows.getFirst();

                    List<ProductOptionItemView> options =
                            groupRows.stream()
                                    .map(row ->
                                            new ProductOptionItemView(
                                                    row.optionId(),
                                                    row.optionName(),
                                                    row.additionalPrice()
                                            )
                                    )
                                    .toList();

                    return new ProductOptionGroupView(
                            first.groupId(),
                            first.groupName(),
                            first.required(),
                            first.selectionType(),
                            options
                    );
                })
                .toList();
    }

    /**
     * 외부에서 전달된 검색 조건을 안전한 기본값으로 정리한다.
     *
     * @param condition 정리할 검색 조건
     * @return 기본값과 정리된 검색어가 적용된 조건
     */
    private ProductSearchCondition normalizeCondition(
            ProductSearchCondition condition
    ) {
        // 검색 조건 자체가 없으면 기본값이 설정된 새 검색 조건을 만든다.
        ProductSearchCondition normalized =
                condition == null
                        ? new ProductSearchCondition()
                        : condition;

        // 검색어의 앞뒤 공백을 제거하고 빈 검색어는 null로 변경한다.
        normalized.setKeyword(normalized.normalizedKeyword());

        // 최소 가격이 없거나 음수이면 기본 최소 가격인 0원으로 변경한다.
        if (normalized.getMinPrice() == null
                || normalized.getMinPrice().signum() < 0) {
            normalized.setMinPrice(DEFAULT_MIN_PRICE);
        }

        // 음수인 최대 가격은 가격 상한이 없는 상태로 변경한다.
        if (normalized.getMaxPrice() != null
                && normalized.getMaxPrice().signum() < 0) {
            normalized.setMaxPrice(null);
        }

        // 최대 가격이 있을 때 최소 가격보다 작으면 두 값을 교환해 유효한 범위로 만든다.
        if (normalized.getMaxPrice() != null
                && normalized.getMinPrice()
                .compareTo(normalized.getMaxPrice()) > 0) {
            BigDecimal originalMinPrice =
                    normalized.getMinPrice();

            normalized.setMinPrice(
                    normalized.getMaxPrice()
            );
            normalized.setMaxPrice(originalMinPrice);
        }

        // 정렬 조건이 없으면 인기순을 기본 정렬로 사용한다.
        if (normalized.getSort() == null) {
            normalized.setSort(ProductSort.POPULAR);
        }

        // 모든 기본값과 보정이 적용된 검색 조건을 반환한다.
        return normalized;
    }
}
