package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.service.OrderAdminService;
import com.cakeshop.domain.order.service.FulfillmentService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderAdminService orderAdminService;
    private final FulfillmentService fulfillmentService;

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

    @PostMapping("/admin/orders/{orderId}/pickup")
    public String markPickedUp(
            @PathVariable long orderId,
            @AuthenticationPrincipal MemberDetails admin
    ) {
        if (admin == null || admin.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        fulfillmentService.markPickedUp(orderId, admin.getMemberId());
        return "redirect:/admin/orders/" + orderId;
    }
}
