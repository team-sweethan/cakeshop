package com.cakeshop.domain.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.service.ProductService;

@Controller
@RequestMapping("/products")
public class ProductController {

    /** 고객에게 공개할 상품 조회 기능을 제공하는 Service. */
    private final ProductService productService;

    /**
     * 상품 조회 Service를 주입받는다.
     *
     * @param productService 고객 상품 조회 Service
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 고객 상품 목록 화면을 반환한다.
     *
     * <p>목록 조회 기능은 이후 단계에서 연결한다.</p>
     *
     * @return 고객 상품 목록 템플릿 경로
     */
    @GetMapping
    public String list() {
        return "customer/product/list";
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

        return "customer/product/detail";
    }
}