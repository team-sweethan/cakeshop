package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.order.service.OrderAdminService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderAdminService orderAdminService;
    private final RefundFacade refundFacade;

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderAdminService.getOrders());
        return "admin/order/list";
    }

    @GetMapping("/admin/orders/{orderId}")
    public String detail(@PathVariable long orderId, Model model) {
        model.addAttribute("order", orderAdminService.getOrder(orderId));
        return "admin/order/detail";
    }

    @PostMapping("/admin/orders/{orderId}/cancel")
    public String cancel(
            @PathVariable long orderId,
            @AuthenticationPrincipal MemberDetails admin,
            @Valid @ModelAttribute CancelForm form,
            RedirectAttributes redirectAttributes
    ) {
        if (admin == null || admin.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        refundFacade.cancelAdminOrder(admin.getMemberId(), orderId, form.getReason());
        redirectAttributes.addFlashAttribute("successMessage", "주문과 결제를 취소했습니다.");
        return "redirect:/admin/orders/" + orderId;
    }
}
