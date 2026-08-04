package com.cakeshop.domain.order.controller.admin;

import com.cakeshop.domain.order.dto.form.admin.FulfillmentSearchCondition;
import com.cakeshop.domain.order.dto.view.admin.FulfillmentListView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.service.admin.FulfillmentService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class FulfillmentAdminController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/admin/fulfillment")
    public String fulfillment(
            @ModelAttribute("condition") FulfillmentSearchCondition condition,
            BindingResult bindingResult,
            Model model
    ) {
        recoverInvalidSearchValues(condition, bindingResult);
        FulfillmentListView fulfillment = fulfillmentService.getFulfillments(condition);
        condition.setPickupDate(fulfillment.pickupDate());
        condition.setStatus(fulfillment.selectedStatus());
        model.addAttribute("fulfillment", fulfillment);
        return "admin/fulfillment/list";
    }

    @PostMapping("/admin/fulfillment/{orderId}/pickup")
    public String markPickedUp(
            @PathVariable("orderId") long orderId,
            @ModelAttribute FulfillmentSearchCondition condition,
            @AuthenticationPrincipal MemberDetails admin,
            RedirectAttributes redirectAttributes
    ) {
        if (admin == null || admin.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        fulfillmentService.markPickedUp(orderId, admin.getMemberId());
        if (condition.getPickupDate() != null) {
            redirectAttributes.addAttribute("pickupDate", condition.getPickupDate().toString()
            );
        }
        if (condition.getStatus() != null) {
            redirectAttributes.addAttribute("status", condition.getStatus().name()
            );
        }
        redirectAttributes.addFlashAttribute("successMessage", "픽업 완료로 변경했습니다.");
        return "redirect:/admin/fulfillment";
    }

    private void recoverInvalidSearchValues(
            FulfillmentSearchCondition condition,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasFieldErrors("pickupDate")) {
            condition.setPickupDate(null);
        }
        if (bindingResult.hasFieldErrors("status")) {
            condition.setStatus(null);
        }
    }
}
