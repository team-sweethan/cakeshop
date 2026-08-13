package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.dto.form.PaymentAdminSearchCondition;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.service.PaymentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** 관리자 결제 내역 조회 화면을 제공한다. */
@Controller
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentAdminService paymentAdminService;

    @GetMapping("/admin/payments")
    public String payments(
            @ModelAttribute("condition") PaymentAdminSearchCondition condition,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasFieldErrors("status")) {
            condition.setStatus(null);
        }
        PaymentAdminListView paymentList = paymentAdminService.getPayments(condition);
        condition.setStatus(paymentList.selectedStatus());
        model.addAttribute("paymentList", paymentList);
        return "admin/payment/list";
    }

    @PostMapping("/admin/payments/{paymentId}/expiration-check")
    public String updateExpirationCheck(
            @PathVariable long paymentId,
            @RequestParam(defaultValue = "false") boolean checked,
            @RequestParam(required = false) PaymentStatus status,
            RedirectAttributes redirectAttributes
    ) {
        paymentAdminService.updateExpirationCheck(paymentId, checked);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                checked ? "결제 만료 확인을 완료했습니다." : "결제 만료 확인을 다시 열었습니다."
        );
        return "redirect:" + paymentRedirectUrl(status);
    }

    private String paymentRedirectUrl(PaymentStatus status) {
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/admin/payments");
        if (status != null) {
            redirect.queryParam("status", status);
        }
        return redirect.toUriString();
    }
}
