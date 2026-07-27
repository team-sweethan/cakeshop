package com.cakeshop.domain.product.admin.controller;

import com.cakeshop.domain.product.admin.dto.form.AdminStockFilter;
import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductAdminController {

    private static final int DEFAULT_PRODUCT_PAGE_SIZE = 10;

    private final ProductAdminService productAdminService;

    public ProductAdminController(
            ProductAdminService productAdminService
    ) {
        this.productAdminService = productAdminService;
    }

    /**
     * 검색 조건에 맞는 관리자 상품 목록 화면을 반환한다.
     *
     * @param condition 상품 검색 및 필터 조건
     * @param bindingResult 검색 조건 바인딩 결과
     * @param page 요청 페이지 번호
     * @param size 한 페이지에 표시할 상품 수
     * @param model 상품 목록과 검색 조건을 전달할 모델
     * @return 관리자 상품 목록 템플릿 경로
     */
    @GetMapping("/admin/products")
    public String products(
            @ModelAttribute("condition")
            ProductAdminSearchCondition condition,

            BindingResult bindingResult,

            @RequestParam(required = false)
            String page,

            @RequestParam(required = false)
            String size,

            Model model
    ) {
        // 잘못된 필터값이 전달되면 해당 조건을 전체 조회 상태로 복구한다.
        recoverInvalidSearchValues(
                condition,
                bindingResult
        );

        // 페이지 번호와 크기를 1 이상의 정수로 변환한다.
        Integer requestedPage =
                parsePositiveInteger(page);

        Integer requestedSize =
                parsePositiveInteger(size);

        // 페이지 크기가 없거나 잘못된 경우 기본값인 10개를 사용한다.
        Integer productPageSize =
                requestedSize == null
                        ? DEFAULT_PRODUCT_PAGE_SIZE
                        : requestedSize;

        // 요청 페이지와 페이지 크기로 페이징 정보를 만든다.
        PageRequest pageRequest =
                new PageRequest(
                        requestedPage,
                        productPageSize
                );

        // 검색 조건과 페이지 정보에 맞는 상품 목록을 조회한다.
        PageResult<ProductAdminListView> pageResult =
                productAdminService.getProducts(
                        condition,
                        pageRequest
                );

        // 상품 목록과 페이징 정보를 화면에 전달한다.
        model.addAttribute(
                "pageResult",
                pageResult
        );

        // 상품 유형 필터 선택지를 화면에 전달한다.
        model.addAttribute(
                "productTypes",
                ProductType.values()
        );

        // 판매 상태 필터 선택지를 화면에 전달한다.
        model.addAttribute(
                "productStatuses",
                ProductStatus.values()
        );

        // 재고 상태 필터 선택지를 화면에 전달한다.
        model.addAttribute(
                "stockFilters",
                AdminStockFilter.values()
        );

        return "admin/product/list";
    }

    /**
     * 검색 조건 바인딩에 실패한 값을 전체 조회 조건으로 복구한다.
     *
     * @param condition 복구할 검색 조건
     * @param bindingResult 요청 파라미터 바인딩 결과
     */
    private void recoverInvalidSearchValues(
            ProductAdminSearchCondition condition,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasFieldErrors("type")) {
            condition.setType(null);
        }

        if (bindingResult.hasFieldErrors("status")) {
            condition.setStatus(null);
        }

        if (bindingResult.hasFieldErrors("stock")) {
            condition.setStock(null);
        }
    }

    /**
     * 문자열을 1 이상의 정수로 변환한다.
     *
     * @param value 요청으로 전달된 문자열
     * @return 변환된 정수, 변환할 수 없으면 {@code null}
     */
    private Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value);

            return parsed > 0
                    ? parsed
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @GetMapping("/admin/products/new")
    public String createForm() {
        return "admin/product/form";
    }

    @GetMapping("/admin/products/{productId}/edit")
    public String editForm(
            @PathVariable long productId
    ) {
        return "admin/product/form";
    }

    /**
     * 상품의 판매 상태를 변경한다.
     *
     * @param productId 상태를 변경할 상품 식별자
     * @param status 변경할 판매 상태
     * @param redirectAttributes 상태 변경 결과 메시지를 전달할 객체
     * @return 관리자 상품 목록으로 이동하는 경로
     */
    @PostMapping("/admin/products/{productId}/status")
    public String changeStatus(
            @PathVariable("productId")
            long productId,

            @RequestParam("status")
            ProductStatus status,

            RedirectAttributes redirectAttributes
    ) {
        // 상품의 판매 상태를 변경한다.
        productAdminService.changeProductStatus(
                productId,
                status
        );

        // 변경된 상태에 맞는 완료 메시지를 만든다.
        String successMessage =
                status == ProductStatus.ACTIVE
                        ? "상품 판매를 시작했습니다."
                        : "상품 판매를 중지했습니다.";

        // 목록 화면으로 이동한 후 완료 메시지를 표시하도록 전달한다.
        redirectAttributes.addFlashAttribute(
                "successMessage",
                successMessage
        );

        // 새로고침으로 상태 변경 요청이 반복되지 않도록 목록으로 리다이렉트한다.
        return "redirect:/admin/products";
    }
}