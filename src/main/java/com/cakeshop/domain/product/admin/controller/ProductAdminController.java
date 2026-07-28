package com.cakeshop.domain.product.admin.controller;

import com.cakeshop.domain.product.admin.dto.form.AdminStockFilter;
import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import jakarta.validation.Valid;
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

    /**
     * 상품 등록 및 수정 화면에서 사용하는 선택지를 전달한다.
     *
     * @param model 선택지를 전달할 모델
     */
    private void addProductFormOptions(Model model) {
        // 활성 카테고리 목록을 전달한다.
        model.addAttribute(
                "categories",
                productAdminService.getActiveCategories()
        );

        // 상품 유형 목록을 전달한다.
        model.addAttribute(
                "productTypes",
                ProductType.values()
        );
    }

    /**
     * 관리자 상품 등록 화면을 반환한다.
     *
     * @param model 상품 등록 폼과 선택지를 전달할 모델
     * @return 상품 등록 템플릿 경로
     */
    @GetMapping("/admin/products/new")
    public String createForm(Model model) {
        // 비어 있는 상품 등록 폼을 화면에 전달한다.
        model.addAttribute(
                "productForm",
                new ProductForm()
        );

        // 카테고리와 상품 유형 선택지를 화면에 전달한다.
        addProductFormOptions(model);

        return "admin/product/form";
    }

    /**
     * 관리자 상품 등록 요청을 처리한다.
     *
     * @param form 상품 등록 입력값
     * @param bindingResult 입력값 검증 결과
     * @param model 검증 실패 시 선택지를 다시 전달할 모델
     * @param redirectAttributes 등록 결과 메시지를 전달할 객체
     * @return 검증 실패 시 등록 화면, 성공 시 상품 목록으로 이동
     */
    @PostMapping("/admin/products")
    public String create(
            @Valid
            @ModelAttribute("productForm")
            ProductForm form,

            BindingResult bindingResult,

            Model model,

            RedirectAttributes redirectAttributes
    ) {
        // 입력값 검증에 실패하면 카테고리와 상품 유형을 다시 전달한다.
        if (bindingResult.hasErrors()) {
            addProductFormOptions(model);

            return "admin/product/form";
        }

        // 검증된 입력값으로 새로운 상품을 등록한다.
        productAdminService.createProduct(form);

        // 리다이렉트된 목록 화면에 등록 완료 메시지를 전달한다.
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "상품을 등록했습니다."
        );

        // 새로고침으로 등록 요청이 반복되지 않도록 목록으로 이동한다.
        return "redirect:/admin/products";
    }

    @GetMapping("/admin/products/{productId}/edit")
    public String editForm(
            @PathVariable long productId
    ) {
        return "redirect:/admin/products";
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