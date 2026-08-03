package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.dto.form.PaymentAdminSearchCondition;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.service.PaymentAdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 관리자 결제 내역 조회 화면을 제공한다. */
@Controller
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentAdminQueryService paymentAdminQueryService;

    @GetMapping("/admin/payments")
    public String payments(
            @ModelAttribute("condition") PaymentAdminSearchCondition condition,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasFieldErrors("status")) {
            condition.setStatus(null);
        }
        PaymentAdminListView paymentList = paymentAdminQueryService.getPayments(condition);
        condition.setStatus(paymentList.selectedStatus());
        model.addAttribute("paymentList", paymentList);
        return "admin/payment/list";
    }
}
