package com.cakeshop.domain.product.controller;

import com.cakeshop.domain.product.dto.form.ProductImageUploadForm;
import com.cakeshop.domain.product.service.ProductImageService;
import com.cakeshop.global.error.BusinessException;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 관리자 상품 이미지 업로드 요청을 처리한다. */
@Controller
@RequestMapping("/admin/products/{productId}/images")
public class ProductImageAdminController {

    private final ProductImageService productImageService;

    public ProductImageAdminController(
            ProductImageService productImageService
    ) {
        this.productImageService = productImageService;
    }

    /** 상품 이미지를 한 장 추가한다. */
    @PostMapping
    public String uploadImage(
            @PathVariable("productId") long productId,
            @Valid
            @ModelAttribute("imageUploadForm")
            ProductImageUploadForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "상품 이미지를 선택해 주세요."
            );

            return redirectToEdit(productId);
        }

        try {
            productImageService.uploadImage(productId, form);
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message()
            );

            return redirectToEdit(productId);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "상품 이미지를 추가했습니다."
        );

        return redirectToEdit(productId);
    }

    private String redirectToEdit(long productId) {
        return "redirect:/admin/products/"
                + productId
                + "/edit";
    }
}
