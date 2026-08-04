package com.cakeshop.domain.order.controller.customer;

import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderCancellationController {

    private final RefundFacade refundFacade;

    @PostMapping("/{orderId}/cancel")
    public String cancel(
            @PathVariable long orderId,
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute CancelForm form
    ) {
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        refundFacade.cancelCustomerOrder(member.getMemberId(), orderId, form.getReason());
        return "redirect:/orders/" + orderId;
    }
}
