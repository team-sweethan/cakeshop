package com.cakeshop.domain.product.controller;

import com.cakeshop.domain.product.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.service.ProductOptionAdminService;
import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.global.error.BusinessException;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products/{productId}")
public class ProductOptionAdminController {

    private final ProductOptionAdminService productOptionAdminService;

    public ProductOptionAdminController(
            ProductOptionAdminService productOptionAdminService
    ) {
        this.productOptionAdminService =
                productOptionAdminService;
    }

    /** 상품 옵션 관리 화면을 반환한다. */
    @GetMapping("/options")
    public String options(
            @PathVariable long productId,
            Model model
    ) {
        ProductOptionManagementView management =
                productOptionAdminService.getOptions(productId);

        model.addAttribute("management", management);
        model.addAttribute(
                "selectionTypes",
                ProductOptionSelectionType.values()
        );
        model.addAttribute(
                "optionStatuses",
                ProductOptionStatus.values()
        );

        return "admin/product/options";
    }

    /** 새로운 옵션 그룹을 등록한다. */
    @PostMapping("/option-groups")
    public String createOptionGroup(
            @PathVariable long productId,
            @Valid
            @ModelAttribute
            ProductOptionGroupForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addValidationError(
                    bindingResult,
                    redirectAttributes
            );
            redirectAttributes.addFlashAttribute(
                    "openCreate",
                    true
            );
            return redirectToOptions(productId);
        }

        long optionGroupId;

        try {
            optionGroupId =
                    productOptionAdminService.createOptionGroup(
                            productId,
                            form
                    );
        } catch (BusinessException exception) {
            return handleRequiredOptionGroupCreationError(
                    exception,
                    productId,
                    redirectAttributes
            );
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "옵션 그룹을 등록했습니다."
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    /** 옵션 그룹 정보와 활성 상태를 수정한다. */
    @PostMapping("/option-groups/{optionGroupId}")
    public String updateOptionGroup(
            @PathVariable long productId,
            @PathVariable long optionGroupId,
            @Valid
            @ModelAttribute
            ProductOptionGroupForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addValidationError(
                    bindingResult,
                    redirectAttributes
            );
            openGroup(redirectAttributes, optionGroupId);
            return redirectToOptions(productId);
        }

        try {
            productOptionAdminService.updateOptionGroup(
                    productId,
                    optionGroupId,
                    form
            );
        } catch (BusinessException exception) {
            return handleRequiredOptionPolicyError(
                    exception,
                    productId,
                    optionGroupId,
                    redirectAttributes
            );
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "옵션 그룹을 수정했습니다."
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    /** 옵션 그룹의 표시 순서를 한 칸 이동한다. */
    @PostMapping("/option-groups/{optionGroupId}/order")
    public String moveOptionGroup(
            @PathVariable long productId,
            @PathVariable long optionGroupId,
            @RequestParam
            ProductOptionMoveDirection direction,
            RedirectAttributes redirectAttributes
    ) {
        productOptionAdminService.moveOptionGroup(
                productId,
                optionGroupId,
                direction
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "옵션 그룹 순서를 변경했습니다."
        );

        return redirectToOptions(productId);
    }

    /** 옵션 그룹에 새로운 개별 옵션을 등록한다. */
    @PostMapping("/option-groups/{optionGroupId}/options")
    public String createOption(
            @PathVariable long productId,
            @PathVariable long optionGroupId,
            @Valid
            @ModelAttribute
            ProductOptionForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addValidationError(
                    bindingResult,
                    redirectAttributes
            );
            openGroup(redirectAttributes, optionGroupId);
            redirectAttributes.addFlashAttribute(
                    "openOptionCreate",
                    optionGroupId
            );
            return redirectToOptions(productId);
        }

        productOptionAdminService.createOption(
                productId,
                optionGroupId,
                form
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "상품 옵션을 등록했습니다."
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    /** 개별 옵션 정보와 활성 상태를 수정한다. */
    @PostMapping(
            "/option-groups/{optionGroupId}/options/{optionId}"
    )
    public String updateOption(
            @PathVariable long productId,
            @PathVariable long optionGroupId,
            @PathVariable long optionId,
            @Valid
            @ModelAttribute
            ProductOptionForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addValidationError(
                    bindingResult,
                    redirectAttributes
            );
            openGroup(redirectAttributes, optionGroupId);
            return redirectToOptions(productId);
        }

        try {
            productOptionAdminService.updateOption(
                    productId,
                    optionGroupId,
                    optionId,
                    form
            );
        } catch (BusinessException exception) {
            return handleRequiredOptionPolicyError(
                    exception,
                    productId,
                    optionGroupId,
                    redirectAttributes
            );
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "상품 옵션을 수정했습니다."
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    /** 개별 옵션의 표시 순서를 한 칸 이동한다. */
    @PostMapping(
            "/option-groups/{optionGroupId}"
                    + "/options/{optionId}/order"
    )
    public String moveOption(
            @PathVariable long productId,
            @PathVariable long optionGroupId,
            @PathVariable long optionId,
            @RequestParam
            ProductOptionMoveDirection direction,
            RedirectAttributes redirectAttributes
    ) {
        productOptionAdminService.moveOption(
                productId,
                optionGroupId,
                optionId,
                direction
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "상품 옵션 순서를 변경했습니다."
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    private void addValidationError(
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        String errorMessage = bindingResult
                .getAllErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력 내용을 확인해 주세요.");

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                errorMessage
        );
    }

    private String redirectToOptions(long productId) {
        return "redirect:/admin/products/"
                + productId
                + "/options";
    }

    private String handleRequiredOptionPolicyError(
            BusinessException exception,
            long productId,
            long optionGroupId,
            RedirectAttributes redirectAttributes
    ) {
        if (exception.getErrorCode()
                != ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY) {
            throw exception;
        }

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                exception.getErrorCode().message()
        );
        openGroup(redirectAttributes, optionGroupId);

        return redirectToOptions(productId);
    }

    private String handleRequiredOptionGroupCreationError(
            BusinessException exception,
            long productId,
            RedirectAttributes redirectAttributes
    ) {
        if (exception.getErrorCode()
                != ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY) {
            throw exception;
        }

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                exception.getErrorCode().message()
        );
        redirectAttributes.addFlashAttribute(
                "openCreate",
                true
        );

        return redirectToOptions(productId);
    }

    private void openGroup(
            RedirectAttributes redirectAttributes,
            long optionGroupId
    ) {
        redirectAttributes.addFlashAttribute(
                "openGroup",
                optionGroupId
        );
    }
}
