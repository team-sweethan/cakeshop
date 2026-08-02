package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/** Toss 결제 승인과 내부 결제 완료 처리를 조정한다. */
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final String TOSS_DONE = "DONE";

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final TossPaymentClient tossPaymentClient;
    private final Clock clock;

    /** 일반 주문의 Toss 결제를 승인하고 내부 상태를 완료한다. */
    public void confirmGeneralPayment(
            long memberId,
            long orderId,
            PaymentConfirmForm form
    ) {
        GeneralPaymentOrder order =
                orderService.getGeneralPaymentOrder(memberId, orderId);
        validatePaymentExpiration(order.paymentExpiresAt());

        Payment payment = paymentService.getReadyPayment(orderId);
        validateRequest(order, payment, form);

        ApprovalResult approval = tossPaymentClient.approve(
                form.getPaymentKey(),
                form.getTossOrderId(),
                form.getAmount().longValueExact(),
                payment.getIdempotencyKey()
        );
        validateApproval(payment, form, approval);

        paymentService.completeGeneralPayment(
                order,
                payment,
                approval
        );
    }

    private void validatePaymentExpiration(
            LocalDateTime paymentExpiresAt
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (paymentExpiresAt == null || !now.isBefore(paymentExpiresAt)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
    }

    private void validateRequest(
            GeneralPaymentOrder order,
            Payment payment,
            PaymentConfirmForm form
    ) {
        if (form == null
                || form.getPaymentKey() == null
                || form.getPaymentKey().isBlank()) {
            throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
        }
        if (!payment.getTossOrderId().equals(form.getTossOrderId())) {
            throw new BusinessException(
                    PaymentErrorCode.TOSS_ORDER_ID_MISMATCH
            );
        }
        if (!sameAmount(order.amount(), form.getAmount())
                || !sameAmount(payment.getAmount(), form.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void validateApproval(
            Payment payment,
            PaymentConfirmForm form,
            ApprovalResult approval
    ) {
        if (approval == null
                || !payment.getTossOrderId().equals(approval.orderId())
                || !TOSS_DONE.equals(approval.status())
                || payment.getAmount().compareTo(
                        BigDecimal.valueOf(approval.totalAmount())
                ) != 0
                || approval.paymentKey() == null
                || !form.getPaymentKey().equals(approval.paymentKey())
                || approval.approvedAt() == null) {
            throw new BusinessException(
                    PaymentErrorCode.TOSS_APPROVAL_FAILED
            );
        }
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected != null
                && actual != null
                && expected.compareTo(actual) == 0;
    }
}
