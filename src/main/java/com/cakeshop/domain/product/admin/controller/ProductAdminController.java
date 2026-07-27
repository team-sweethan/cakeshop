package com.cakeshop.domain.product.admin.controller;

import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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
     * 관리자 전체 상품 목록 화면을 반환한다.
     *
     * @param page 요청 페이지 번호
     * @param size 한 페이지에 표시할 상품 수
     * @param model 상품 목록과 페이지 정보를 전달할 모델
     * @return 관리자 상품 목록 템플릿 경로
     */
    @GetMapping("/admin/products")
    public String products(
            @RequestParam(required = false)
            Integer page,

            @RequestParam(required = false)
            Integer size,

            Model model
    ) {
        // size가 전달되지 않으면 관리자 목록의 기본 크기인 10개를 사용한다.
        Integer productPageSize =
                size == null
                        ? DEFAULT_PRODUCT_PAGE_SIZE
                        : size;

        // 요청받은 페이지 번호와 페이지 크기로 페이징 정보를 만든다.
        PageRequest pageRequest =
                new PageRequest(page, productPageSize);

        // 현재 페이지에 표시할 전체 상품 목록을 조회한다.
        PageResult<ProductAdminListView> pageResult =
                productAdminService.getProducts(pageRequest);

        // 상품 목록과 페이지 정보를 관리자 목록 화면에 전달한다.
        model.addAttribute("pageResult", pageResult);

        return "admin/product/list";
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
}