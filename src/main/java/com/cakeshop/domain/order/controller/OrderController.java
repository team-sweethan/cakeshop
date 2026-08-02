package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.order.service.OrderQueryService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/pickup")
    public String pickupSetting() {
        return "customer/order/pickup-setting";
    }

    @GetMapping("/custom/options")
    public String customOptions() {
        return "customer/order/custom-option";
    }

    @GetMapping("/custom/request")
    public String customRequest() {
        return "customer/order/custom-request";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "customer/order/form";
    }

    @GetMapping("/complete")
    public String complete() {
        return "customer/order/complete";
    }

    @GetMapping
    public String orders(
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        model.addAttribute(
                "orders",
                orderQueryService.getMemberOrders(requireMemberId(member))
        );
        return "customer/order/detail";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        long memberId = requireMemberId(member);
        model.addAttribute("orders", orderQueryService.getMemberOrders(memberId));
        model.addAttribute("order", orderQueryService.getMemberOrder(memberId, orderId));
        return "customer/order/detail";
    }

    private long requireMemberId(MemberDetails member) {
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member.getMemberId();
    }
}
