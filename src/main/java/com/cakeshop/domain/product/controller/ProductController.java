package com.cakeshop.domain.product.controller;

import java.math.BigDecimal;

import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.form.StockFilter;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.service.ReviewProductQueryService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/products")
public class ProductController {

    /** 고객 상품 목록의 기본 페이지 크기. */
    private static final int DEFAULT_PRODUCT_PAGE_SIZE = 8;

    /** 고객에게 공개할 상품 조회 기능을 제공하는 Service. */
    private final ProductService productService;

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 시은
     * 작성일 : 2026-08-10
     * 기능 : 상품 상세 후기 미리보기 주입
     * 설명 : 상품 상세에 붙일 최신 후기 3건을 리뷰 도메인 계약으로 받는다.
     *        상품이 reviews 를 직접 조회하지 않는다. 붙이는 방식은 2026-08-06 합의를 따른다.
     * ******************************
     */
    private final ReviewProductQueryService reviewProductQueryService;

    /**
     * 상품 조회 Service와 후기 조회 계약을 주입받는다.
     *
     * @param productService 고객 상품 조회 Service
     * @param reviewProductQueryService 상품 상세에 붙일 후기 조회 계약
     */
    public ProductController(
            ProductService productService,
            ReviewProductQueryService reviewProductQueryService
    ) {
        this.productService = productService;
        this.reviewProductQueryService = reviewProductQueryService;
    }

    /**
     * 검색 조건에 맞는 고객 상품 목록 화면을 반환한다.
     *
     * @param condition 검색·필터·정렬 조건
     * @param page 요청 페이지 번호
     * @param size 한 페이지에 표시할 상품 수
     * @param model 상품 목록과 검색 조건을 전달할 모델
     * @return 고객 상품 목록 템플릿 경로
     */
    @GetMapping
    public String list(
            @ModelAttribute("condition")
            ProductSearchCondition condition,

            BindingResult bindingResult,

            @RequestParam(required = false)
            String page,

            @RequestParam(required = false)
            String size,

            Model model
    ) {
        // URL에 잘못된 필터값이 들어와도 목록 화면이 오류로 끝나지 않도록 기본값으로 복구한다.
        recoverInvalidSearchValues(condition, bindingResult);

        Integer requestedPage = parsePositiveInteger(page);
        Integer requestedSize = parsePositiveInteger(size);

        // size가 없으면 상품 카드 그리드에 맞춰 한 페이지에 8개를 표시한다.
        Integer productPageSize =
                requestedSize == null
                        ? DEFAULT_PRODUCT_PAGE_SIZE
                        : requestedSize;

        PageRequest pageRequest =
                new PageRequest(requestedPage, productPageSize);

        // 검색 조건과 페이지 요청을 사용해 고객 상품 목록을 조회한다.
        PageResult<ProductListView> pageResult =
                productService.getPublicProducts(
                        condition,
                        pageRequest
                );

        // 상품 목록과 페이지 정보를 화면에 전달한다.
        model.addAttribute("pageResult", pageResult);

        // 화면에서 상품 유형 필터를 출력할 수 있도록 전달한다.
        model.addAttribute(
                "productTypes",
                ProductType.values()
        );

        // 화면에서 재고 상태 필터를 출력할 수 있도록 전달한다.
        model.addAttribute(
                "stockFilters",
                StockFilter.values()
        );

        // 화면에서 정렬 버튼을 출력할 수 있도록 전달한다.
        model.addAttribute(
                "sortOptions",
                ProductSort.values()
        );

        return "customer/product/list";
    }

    /**
     * 검색 조건 바인딩에 실패한 필드를 고객 목록의 안전한 기본값으로 복구한다.
     *
     * @param condition 복구할 검색 조건
     * @param bindingResult 요청 파라미터 바인딩 결과
     */
    private void recoverInvalidSearchValues(
            ProductSearchCondition condition,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasFieldErrors("type")) {
            condition.setType(null);
        }

        if (bindingResult.hasFieldErrors("stock")) {
            condition.setStock(null);
        }

        if (bindingResult.hasFieldErrors("sort")) {
            condition.setSort(ProductSort.POPULAR);
        }

        if (bindingResult.hasFieldErrors("minPrice")) {
            condition.setMinPrice(BigDecimal.ZERO);
        }

        if (bindingResult.hasFieldErrors("maxPrice")) {
            condition.setMaxPrice(null);
        }
    }

    /**
     * 페이지 관련 문자열을 1 이상의 정수로 변환한다.
     *
     * @param value 요청으로 전달된 문자열
     * @return 1 이상의 정수, 변환할 수 없으면 {@code null}
     */
    private Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 고객 상품 상세 정보를 조회하여 상세 화면을 반환한다.
     *
     * @param productId 조회할 상품 식별자
     * @param model 상품 정보를 템플릿으로 전달할 모델
     * @return 고객 상품 상세 템플릿 경로
     */
    @GetMapping("/{productId:\\d+}")
    public String detail(
            @PathVariable("productId") long productId,
            Model model
    ) {
        ProductDetailView product =
                productService.getPublicDetail(productId);

        model.addAttribute("product", product);
        model.addAttribute(
                "optionGroups",
                productService.getPublicOptionGroups(productId)
        );

        // ProductController 수정: HyunGyu-Cho
        model.addAttribute(
                "reviewPreviews",
                reviewProductQueryService.getPreview(productId)
        );

        return "customer/product/detail";
    }
}
