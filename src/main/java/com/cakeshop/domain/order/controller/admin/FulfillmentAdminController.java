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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequiredArgsConstructor
public class FulfillmentAdminController {

    private static final Set<OrderStatus> FULFILLMENT_STATUSES = Set.of(
            OrderStatus.UNDER_REVIEW,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.PICKED_UP
    );

    private final FulfillmentService fulfillmentService;

    @GetMapping("/admin/fulfillment")
    public String fulfillment(
            @ModelAttribute("condition") FulfillmentSearchCondition condition,
            BindingResult bindingResult,
            Model model
    ) {
        // 검증
        recoverInvalidSearchValues(condition, bindingResult);
        FulfillmentListView fulfillment = fulfillmentService.getFulfillments(condition);
        condition.setPickupDate(fulfillment.pickupDate());
        condition.setStatus(fulfillment.selectedStatus());
        model.addAttribute("fulfillment", fulfillment);
        return "admin/fulfillment/list";
    }

    /** 픽업 대기 -> 픽업 완료**/
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

        redirectAttributes.addFlashAttribute("successMessage", "픽업 완료로 변경했습니다.");
        return "redirect:" + fulfillmentRedirectUrl(condition);
    }

    /** 배송 상태 (픽업 날짜가 없던가, 상태가 없던) 검증 로직.**/
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

    private String fulfillmentRedirectUrl(FulfillmentSearchCondition condition) {
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/admin/fulfillment");
        if (condition != null && condition.getPickupDate() != null) {
            redirect.queryParam("pickupDate", condition.getPickupDate());
        }
        if (condition != null
                && condition.getStatus() != null
                && FULFILLMENT_STATUSES.contains(condition.getStatus())) {
            redirect.queryParam("status", condition.getStatus());
        }
        return redirect.toUriString();
    }
}
