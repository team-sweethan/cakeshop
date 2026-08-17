package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderPaymentQueryService;
import com.cakeshop.domain.order.service.OrderPaymentQueryService.PaymentOrder;
import com.cakeshop.domain.order.service.OrderPaymentQueryService.PaymentExecutionOrder;
import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.service.PaymentRecoveryService.CompensationRequest;
import com.cakeshop.domain.payment.service.TossPaymentApprovalResolver.ApprovalResolution;
import com.cakeshop.domain.payment.service.TossPaymentApprovalResolver.ApprovalState;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Toss 결제 confirm과 보상 복구의 실행 순서를 조정한다. */
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final OrderPaymentQueryService orderPaymentQueryService;
    private final PaymentService paymentService;
    private final PaymentCompensationProcessor compensationProcessor;
    private final TossPaymentApprovalResolver approvalResolver;
    private final Clock clock;

    /** 일반·수제 주문의 Toss 결제를 승인하고 내부 상태를 완료한다. */
    public void confirmPayment(long memberId, long orderId, PaymentConfirmForm form) {
        PaymentOrder ownedOrder = orderPaymentQueryService.getMemberPaymentOrder(memberId, orderId);
        Payment completedPayment = paymentService.findDonePayment(orderId).orElse(null);
        if (completedPayment != null) {
            validateCompletedRequest(ownedOrder, completedPayment, form);
            validateCompletedProviderState(completedPayment, form);
            return;
        }

        PaymentExecutionOrder order = orderPaymentQueryService
                .getMemberPaymentExecutionOrder(memberId, orderId);
        validatePaymentExpiration(order.paymentExpiresAt());

        Payment payment = paymentService.getReadyPayment(orderId);
        validateRequest(order, payment, form);
        ResolvedApproval resolved = resolvePreparedOrNewApproval(payment, form, orderId);

        try {
            // Toss 승인 응답을 받은 뒤에도 내부 완료 직전에 만료 경계를 다시 확인한다.
            validatePaymentExpiration(order.paymentExpiresAt());
            paymentService.completePayment(order, resolved.payment(), resolved.approval().approval());
        } catch (RuntimeException exception) {
            Payment concurrentlyCompleted = findConcurrentlyCompleted(orderId);
            if (concurrentlyCompleted == null) {
                throw compensationProcessor.compensateApproved(resolved.payment(), form.getPaymentKey());
            }
            validateCompletedRequest(ownedOrder, concurrentlyCompleted, form);
        }
    }

    /** 0원 주문은 Toss 승인 요청 없이 서버가 READY 결제를 완료한다. */
    public void completeZeroAmountGeneralPayment(long memberId, long orderId) {
        // 브라우저 재전송은 이미 완료된 0원 결제를 성공으로 간주하되, 회원 소유권은 먼저 확인한다.
        orderPaymentQueryService.getMemberPaymentOrder(memberId, orderId);
        Payment completedPayment = paymentService.findDonePayment(orderId).orElse(null);
        if (completedPayment != null) {
            if (completedPayment.getAmount() == null || completedPayment.getAmount().signum() != 0) {
                throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
            }
            return;
        }
        PaymentExecutionOrder order = orderPaymentQueryService.getMemberGeneralPaymentOrder(memberId, orderId);
        if (order.amount().signum() != 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        // 일반 PG 승인 경로와 동일하게 완료 직전에도 결제 가능 시간을 다시 확인한다.
        validatePaymentExpiration(order.paymentExpiresAt());
        Payment payment = paymentService.getReadyPayment(orderId);
        paymentService.completeZeroAmountGeneralPayment(order, payment, LocalDateTime.now(clock));
    }

    /** 영속화된 미완료 보상 취소를 같은 멱등키로 다시 처리한다. */
    public void recoverPendingCompensations(int batchSize) {
        for (CompensationRequest request : compensationProcessor.getPreparedCompensations(batchSize)) {
            try {
                ApprovalResolution approvalState = approvalResolver.resolveCompensationState(request);
                if (approvalState.state() == ApprovalState.NOT_APPROVED) {
                    compensationProcessor.releaseUnapproved(request);
                    continue;
                }
                if (approvalState.state() == ApprovalState.IN_PROGRESS) {
                    continue;
                }
                compensationProcessor.completePrepared(request);
            } catch (RuntimeException recoveryFailure) {
                log.warn("Pending payment compensation could not be completed.");
            }
        }
    }

    private ResolvedApproval resolvePreparedOrNewApproval(
            Payment payment,
            PaymentConfirmForm form,
            long orderId
    ) {
        Optional<CompensationRequest> prepared = compensationProcessor.findPrepared(payment);
        if (prepared.isPresent()) {
            ApprovalResolution recoveryState = approvalResolver.resolveCompensationState(prepared.get());
            if (recoveryState.state() == ApprovalState.NOT_APPROVED) {
                compensationProcessor.releaseUnapproved(prepared.get());
                payment = paymentService.getReadyPayment(orderId);
            } else if (recoveryState.state() == ApprovalState.IN_PROGRESS) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
            } else if (recoveryState.state() == ApprovalState.DONE
                    && prepared.get().paymentKey().equals(form.getPaymentKey())) {
                return new ResolvedApproval(
                        payment,
                        requireApproved(approvalResolver.resolveCurrent(payment, form))
                );
            } else if (recoveryState.state() == ApprovalState.CANCELED) {
                throw compensationProcessor.reconcileCanceled(payment, recoveryState.lookup());
            } else {
                throw compensationProcessor.cancelPrepared(prepared.get());
            }
        }

        ApprovalResolution current = approvalResolver.resolveCurrent(payment, form);
        if (current.state() == ApprovalState.DONE) {
            return new ResolvedApproval(payment, requireApproved(current));
        }
        if (current.state() == ApprovalState.CANCELED) {
            throw compensationProcessor.reconcileCanceled(payment, current.lookup());
        }

        compensationProcessor.prepareBeforeApproval(payment, form.getPaymentKey());
        ApprovalResolution approved = approvalResolver.approve(payment, form);
        if (approved.state() == ApprovalState.DONE) {
            return new ResolvedApproval(payment, requireApproved(approved));
        }
        if (approved.state() == ApprovalState.CANCELED) {
            throw compensationProcessor.reconcileCanceled(payment, approved.lookup());
        }
        throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
    }

    private ApprovalResolution requireApproved(ApprovalResolution resolution) {
        if (resolution.state() != ApprovalState.DONE || resolution.approval() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_LOOKUP_FAILED);
        }
        return resolution;
    }

    private Payment findConcurrentlyCompleted(long orderId) {
        try {
            return paymentService.findDonePayment(orderId).orElse(null);
        } catch (RuntimeException lookupFailure) {
            return null;
        }
    }

    private void validateCompletedRequest(PaymentOrder order, Payment payment, PaymentConfirmForm form) {
        if (form == null
                || payment.getPaymentKey() == null
                || !payment.getPaymentKey().equals(form.getPaymentKey())) {
            throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
        }
        if (!payment.getTossOrderId().equals(form.getTossOrderId())
                || !order.orderNumber().equals(form.getTossOrderId())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (!sameAmount(order.finalAmount(), form.getAmount())
                || !sameAmount(payment.getAmount(), form.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void validateCompletedProviderState(Payment payment, PaymentConfirmForm form) {
        ApprovalResolution resolution = approvalResolver.resolveCurrent(payment, form);
        if (resolution.state() == ApprovalState.CANCELED) {
            throw compensationProcessor.reconcileCanceled(payment, resolution.lookup());
        }
        requireApproved(resolution);
    }

    private void validatePaymentExpiration(LocalDateTime paymentExpiresAt) {
        if (paymentExpiresAt == null || !LocalDateTime.now(clock).isBefore(paymentExpiresAt)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
    }

    private void validateRequest(PaymentExecutionOrder order, Payment payment, PaymentConfirmForm form) {
        if (form == null || form.getPaymentKey() == null || form.getPaymentKey().isBlank()) {
            throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
        }
        if (!payment.getTossOrderId().equals(form.getTossOrderId())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (!sameAmount(order.amount(), form.getAmount())
                || !sameAmount(payment.getAmount(), form.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected != null && actual != null && expected.compareTo(actual) == 0;
    }

    private record ResolvedApproval(Payment payment, ApprovalResolution approval) {
    }
}
