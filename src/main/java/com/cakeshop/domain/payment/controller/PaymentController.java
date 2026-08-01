package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.service.PaymentFacade;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @GetMapping("/orders/{orderId:\\d+}/payment")
    public String payment(@PathVariable("orderId") long orderId) {
        return "customer/payment/form";
    }

    /** Toss 결제 인증 성공 값을 검증하고 일반 주문의 결제를 완료한다. */
    @PostMapping("/orders/{orderId:\\d+}/payment/confirm")
    public String confirm(
            @PathVariable("orderId") long orderId,
            @Valid @ModelAttribute PaymentConfirmForm form,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        paymentFacade.confirmGeneralPayment(
                memberDetails.getMemberId(),
                orderId,
                form
        );

        return "redirect:/orders/complete?orderId=" + orderId;
    }
}
