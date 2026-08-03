package com.cakeshop.domain.order.controller;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.service.OrderCheckoutService;
import com.cakeshop.domain.order.service.OrderQueryService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCheckoutService orderCheckoutService;
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final MemberService memberService;

    /** 장바구니 항목의 픽업 일시 수정용 목업 경로다. 일반 주문은 checkout에서 선택한다. */
    @GetMapping(value = "/pickup", params = "intent=cart-edit")
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
    public String checkout(
            @ModelAttribute("orderForm") GeneralOrderForm form,
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        prefillMemberContact(form, requireMember(member));
        form.setRequestKey(UUID.randomUUID().toString());
        return renderCheckout(form, model);
    }

    @PostMapping("/general")
    public String createGeneralOrder(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("orderForm") GeneralOrderForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            if (hasInvalidOrderItem(bindingResult)) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            return renderCheckout(form, model);
        }

        long orderId = orderService.createGeneralOrder(
                requireMemberId(member),
                form
        );
        return "redirect:/orders/" + orderId + "/payment";
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

    private String renderCheckout(GeneralOrderForm form, Model model) {
        model.addAttribute(
                "checkout",
                orderCheckoutService.getGeneralCheckout(
                        form.getProductId(),
                        form.getQuantity(),
                        form.getOptionIds()
                )
        );
        return "customer/order/form";
    }

    private boolean hasInvalidOrderItem(BindingResult bindingResult) {
        return bindingResult.hasFieldErrors("productId")
                || bindingResult.hasFieldErrors("quantity")
                || bindingResult.hasFieldErrors("optionIds");
    }

    /** 최초 주문서 진입 시 최신 회원 연락처를 주문자·픽업자 기본값으로 사용한다. */
    private void prefillMemberContact(
            GeneralOrderForm form,
            MemberDetails member
    ) {
        MemberProfileView profile = memberService.getMemberProfile(
                member.getUsername()
        );
        form.setOrdererName(profile.name());
        form.setOrdererPhone(profile.phone());
        form.setPickupName(profile.name());
        form.setPickupPhone(profile.phone());
    }

    private MemberDetails requireMember(MemberDetails member) {
        requireMemberId(member);
        return member;
    }

    private long requireMemberId(MemberDetails member) {
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member.getMemberId();
    }
}
