package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.PaymentLookupResult;
import com.cakeshop.domain.payment.service.PaymentRecoveryService.CompensationRequest;
import com.cakeshop.global.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 승인 후 내부 처리 실패를 보상 취소하고, 재시도 가능한 로컬 상태로 반영한다. */
@Component
public class PaymentCompensationProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensationProcessor.class);

    private final PaymentRecoveryService paymentRecoveryService;
    private final TossPaymentClient tossPaymentClient;

    public PaymentCompensationProcessor(
            PaymentRecoveryService paymentRecoveryService,
            TossPaymentClient tossPaymentClient
    ) {
        this.paymentRecoveryService = paymentRecoveryService;
        this.tossPaymentClient = tossPaymentClient;
    }

    public Optional<CompensationRequest> findPrepared(Payment payment) {
        return paymentRecoveryService.findPreparedCompensation(payment);
    }

    public void releaseUnapproved(CompensationRequest request) {
        paymentRecoveryService.releaseUnapprovedCompensation(request);
    }

    public List<CompensationRequest> getPreparedCompensations(int batchSize) {
        return paymentRecoveryService.getPreparedCompensations(batchSize);
    }

    public void prepareBeforeApproval(Payment payment, String paymentKey) {
        try {
            CompensationRequest request = paymentRecoveryService.createRequest(payment, paymentKey);
            paymentRecoveryService.prepareCompensation(request);
        } catch (RuntimeException persistenceFailure) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
    }

    public BusinessException compensateApproved(Payment payment, String paymentKey) {
        CompensationRequest request = paymentRecoveryService.createRequest(payment, paymentKey);
        try {
            paymentRecoveryService.prepareCompensation(request);
        } catch (RuntimeException prepareFailure) {
            log.warn("Payment compensation request could not be persisted before provider cancel.");
            return new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
        execute(request);
        return new BusinessException(PaymentErrorCode.PAYMENT_COMPENSATED);
    }

    public BusinessException reconcileCanceled(Payment payment, PaymentLookupResult lookup) {
        CancellationResult result = TossCancellationResultResolver.findCompleted(lookup)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING));
        CompensationRequest request = paymentRecoveryService.createRequest(payment, lookup.paymentKey());
        try {
            paymentRecoveryService.prepareCompensation(request);
            paymentRecoveryService.completeCompensation(request, result);
        } catch (RuntimeException recoveryFailure) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
        return new BusinessException(PaymentErrorCode.PAYMENT_COMPENSATED);
    }

    public void completePrepared(CompensationRequest request) {
        execute(request);
    }

    public BusinessException cancelPrepared(CompensationRequest request) {
        execute(request);
        return new BusinessException(PaymentErrorCode.PAYMENT_COMPENSATED);
    }

    private void execute(CompensationRequest request) {
        CancellationResult result;
        try {
            result = tossPaymentClient.cancel(request.paymentKey(), request.reason(), request.idempotencyKey());
        } catch (BusinessException cancelFailure) {
            result = TossCancellationResultResolver.findCompleted(tossPaymentClient, request.paymentKey())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING));
        }

        try {
            paymentRecoveryService.completeCompensation(request, result);
        } catch (RuntimeException completionFailure) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
    }

}
