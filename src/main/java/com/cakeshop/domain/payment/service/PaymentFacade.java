package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.service.customer.CustomerOrderQueryService;
import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.PaymentLookupResult;
import com.cakeshop.domain.payment.service.PaymentRecoveryService.CompensationRequest;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Toss 결제 승인과 내부 결제 완료 처리를 조정한다. */
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final String TOSS_DONE = "DONE";
    private static final String TOSS_CANCELED = "CANCELED";
    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final OrderService orderService;
    private final CustomerOrderQueryService orderQueryService;
    private final PaymentService paymentService;
    private final PaymentRecoveryService paymentRecoveryService;
    private final TossPaymentClient tossPaymentClient;
    private final Clock clock;

    /** 일반 주문의 Toss 결제를 승인하고 내부 상태를 완료한다. */
    public void confirmGeneralPayment(
            long memberId,
            long orderId,
            PaymentConfirmForm form
    ) {
        OrderDetailView ownedOrder = orderQueryService.getMemberOrder(
                memberId,
                orderId
        );
        Payment completedPayment = paymentService.findDonePayment(orderId)
                .orElse(null);
        if (completedPayment != null) {
            validateCompletedRequest(ownedOrder, completedPayment, form);
            validateCompletedProviderState(completedPayment, form);
            return;
        }

        GeneralPaymentOrder order =
                orderService.getGeneralPaymentOrder(memberId, orderId);
        validatePaymentExpiration(order.paymentExpiresAt());

        Payment payment = paymentService.getReadyPayment(orderId);
        validateRequest(order, payment, form);

        ApprovalResult approval = null;
        Optional<CompensationRequest> preparedCompensation =
                paymentRecoveryService.findPreparedCompensation(payment);
        if (preparedCompensation.isPresent()) {
            CompensationRequest request = preparedCompensation.get();
            ApprovalStateResult approvalState = getApprovalState(request);
            if (approvalState.state() == ApprovalState.NOT_APPROVED) {
                paymentRecoveryService.releaseUnapprovedCompensation(request);
                // 새 paymentKey로 재시도할 때 교체된 승인 멱등키를 다시 읽는다.
                payment = paymentService.getReadyPayment(orderId);
            } else if (approvalState.state() == ApprovalState.IN_PROGRESS) {
                // PG 처리 중에는 보상 요청을 해제하거나 새 승인 요청을 보내지 않는다.
                throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
            } else if (approvalState.state() == ApprovalState.DONE
                    && request.paymentKey().equals(form.getPaymentKey())) {
                // 같은 성공 콜백의 중복 요청이면 정상 내부 완료를 이어간다.
                validateLookup(payment, form, approvalState.lookup());
                approval = approvalState.lookup().toApprovalResult();
            } else if (approvalState.state() == ApprovalState.CANCELED) {
                throw reconcileCanceledPayment(payment, approvalState.lookup());
            } else {
                throw cancelAndCompleteCompensation(request);
            }
        }

        if (approval == null) {
            approval = resolveApproval(payment, form);
        }
        validateApproval(payment, form, approval);

        try {
            paymentService.completeGeneralPayment(
                    order,
                    payment,
                    approval
            );
        } catch (RuntimeException exception) {
            Payment concurrentlyCompleted = findConcurrentlyCompleted(orderId);
            if (concurrentlyCompleted == null) {
                throw compensateApprovedPayment(payment, form.getPaymentKey());
            }
            validateCompletedRequest(
                    ownedOrder,
                    concurrentlyCompleted,
                    form
            );
        }
    }

    /** 영속화된 미완료 보상 취소를 같은 멱등키로 다시 처리한다. */
    public void recoverPendingCompensations(int batchSize) {
        for (CompensationRequest request
                : paymentRecoveryService.getPreparedCompensations(batchSize)) {
            try {
                ApprovalStateResult approvalState = getApprovalState(request);
                if (approvalState.state() == ApprovalState.NOT_APPROVED) {
                    // 승인되지 않은 요청은 보상을 해제해 만료 처리로 돌아갈 수 있게 한다.
                    paymentRecoveryService.releaseUnapprovedCompensation(request);
                    continue;
                }
                if (approvalState.state() == ApprovalState.IN_PROGRESS) {
                    // PG 결과가 확정될 때까지 보호 요청을 유지한다.
                    continue;
                }
                executeCompensation(request);
            } catch (RuntimeException recoveryFailure) {
                log.warn("Pending payment compensation could not be completed.");
            }
        }
    }

    /** 현재 PG 상태를 먼저 대조하고, 미승인 상태일 때만 같은 멱등키로 승인한다. */
    private ApprovalResult resolveApproval(
            Payment payment,
            PaymentConfirmForm form
    ) {
        Optional<PaymentLookupResult> current = tossPaymentClient.find(
                form.getPaymentKey()
        );
        if (current.isPresent()) {
            Optional<ApprovalResult> resolved = resolveLookup(
                    payment,
                    form,
                    current.get()
            );
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }

        prepareApprovalRecovery(payment, form.getPaymentKey());

        try {
            return tossPaymentClient.approve(
                    form.getPaymentKey(),
                    form.getTossOrderId(),
                    form.getAmount().longValueExact(),
                    payment.getIdempotencyKey()
            );
        } catch (BusinessException approvalFailure) {
            // timeout·연결 단절처럼 승인 결과가 불명확하면
            // paymentKey 조회 결과를 정본으로 삼는다.
            Optional<PaymentLookupResult> afterFailure;
            try {
                afterFailure = tossPaymentClient.find(form.getPaymentKey());
            } catch (BusinessException lookupFailure) {
                throw persistUncertainApproval(payment, form.getPaymentKey());
            }
            if (afterFailure.isEmpty()) {
                throw persistUncertainApproval(payment, form.getPaymentKey());
            }
            return resolveLookup(payment, form, afterFailure.get())
                    .orElseThrow(() -> persistUncertainApproval(
                            payment,
                            form.getPaymentKey()
                    ));
        }
    }

    private BusinessException persistUncertainApproval(
            Payment payment,
            String paymentKey
    ) {
        // 승인 호출 전에 REQUESTED 보상 요청을 저장했으므로, 이 시점에는 재처리 대상이 보장된다.
        return new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
    }

    private void prepareApprovalRecovery(Payment payment, String paymentKey) {
        try {
            CompensationRequest request = paymentRecoveryService.createRequest(payment, paymentKey);
            paymentRecoveryService.prepareCompensation(request);
        } catch (RuntimeException persistenceFailure) {
            // 복구 요청을 보장할 수 없으면 PG 승인 자체를 호출하지 않는다.
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
    }

    private Optional<ApprovalResult> resolveLookup(
            Payment payment,
            PaymentConfirmForm form,
            PaymentLookupResult lookup
    ) {
        validateLookup(payment, form, lookup);
        if (TOSS_DONE.equals(lookup.status())) {
            return Optional.of(lookup.toApprovalResult());
        }
        if (TOSS_CANCELED.equals(lookup.status())) {
            throw reconcileCanceledPayment(payment, lookup);
        }
        if ("READY".equals(lookup.status())
                || "IN_PROGRESS".equals(lookup.status())) {
            return Optional.empty();
        }
        throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
    }

    private ApprovalStateResult getApprovalState(CompensationRequest request) {
        try {
            Optional<PaymentLookupResult> current = tossPaymentClient.find(request.paymentKey());
            if (current.isEmpty()) {
                return new ApprovalStateResult(ApprovalState.NOT_APPROVED, null);
            }
            PaymentLookupResult lookup = current.get();
            ApprovalState state = switch (lookup.status()) {
                case "READY", "ABORTED", "EXPIRED" -> ApprovalState.NOT_APPROVED;
                case "IN_PROGRESS" -> ApprovalState.IN_PROGRESS;
                case TOSS_DONE -> ApprovalState.DONE;
                case TOSS_CANCELED -> ApprovalState.CANCELED;
                default -> ApprovalState.FINAL_OR_UNKNOWN;
            };
            return new ApprovalStateResult(state, lookup);
        } catch (BusinessException lookupFailure) {
            // 상태 조회 실패 때는 보상 요청을 유지해 승인 결과 유실을 막는다.
            return new ApprovalStateResult(ApprovalState.FINAL_OR_UNKNOWN, null);
        }
    }

    private void validateLookup(
            Payment payment,
            PaymentConfirmForm form,
            PaymentLookupResult lookup
    ) {
        if (lookup == null
                || !form.getPaymentKey().equals(lookup.paymentKey())) {
            throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
        }
        if (!payment.getTossOrderId().equals(lookup.orderId())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (payment.getAmount().compareTo(
                BigDecimal.valueOf(lookup.totalAmount())
        ) != 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private Payment findConcurrentlyCompleted(long orderId) {
        try {
            return paymentService.findDonePayment(orderId).orElse(null);
        } catch (RuntimeException lookupFailure) {
            // DB 장애로 완료 여부도 확인할 수 없으면 고객 과금보다 자동 취소를 우선한다.
            return null;
        }
    }

    private BusinessException compensateApprovedPayment(
            Payment payment,
            String paymentKey
    ) {
        CompensationRequest request = paymentRecoveryService.createRequest(
                payment,
                paymentKey
        );
        try {
            paymentRecoveryService.prepareCompensation(request);
        } catch (RuntimeException prepareFailure) {
            // 재시도할 보상 요청을 저장하지 못하면 외부 취소 결과도 수습할 수 없다.
            log.warn(
                    "Payment compensation request could not be persisted before provider cancel."
            );
            return new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }

        return cancelAndCompleteCompensation(request);
    }

    private BusinessException cancelAndCompleteCompensation(
            CompensationRequest request
    ) {
        executeCompensation(request);
        return new BusinessException(PaymentErrorCode.PAYMENT_COMPENSATED);
    }

    private void executeCompensation(CompensationRequest request) {
        CancellationResult result;
        try {
            result = tossPaymentClient.cancel(
                    request.paymentKey(),
                    request.reason(),
                    request.idempotencyKey()
            );
        } catch (BusinessException cancelFailure) {
            result = findCanceledResult(request.paymentKey())
                    .orElseThrow(() -> new BusinessException(
                            PaymentErrorCode.PAYMENT_RECOVERY_PENDING
                    ));
        }

        try {
            paymentRecoveryService.completeCompensation(request, result);
        } catch (RuntimeException completionFailure) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
    }

    private BusinessException reconcileCanceledPayment(
            Payment payment,
            PaymentLookupResult lookup
    ) {
        CancellationResult result = Optional.ofNullable(lookup.cancellation())
                .filter(this::isCompletedCancellation)
                .orElseThrow(() -> new BusinessException(
                        PaymentErrorCode.PAYMENT_RECOVERY_PENDING
                ));
        CompensationRequest request = paymentRecoveryService.createRequest(
                payment,
                lookup.paymentKey()
        );
        try {
            paymentRecoveryService.prepareCompensation(request);
            paymentRecoveryService.completeCompensation(request, result);
        } catch (RuntimeException recoveryFailure) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
        return new BusinessException(PaymentErrorCode.PAYMENT_COMPENSATED);
    }

    private Optional<CancellationResult> findCanceledResult(String paymentKey) {
        return tossPaymentClient.find(paymentKey)
                .filter(lookup -> TOSS_CANCELED.equals(lookup.status()))
                .map(PaymentLookupResult::cancellation)
                .filter(this::isCompletedCancellation);
    }

    private boolean isCompletedCancellation(CancellationResult result) {
        return result != null
                && TOSS_CANCELED.equals(result.status())
                && result.transactionKey() != null
                && !result.transactionKey().isBlank()
                && result.canceledAt() != null;
    }

    private void validateCompletedRequest(
            OrderDetailView order,
            Payment payment,
            PaymentConfirmForm form
    ) {
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

    private void validateCompletedProviderState(
            Payment payment,
            PaymentConfirmForm form
    ) {
        PaymentLookupResult lookup = tossPaymentClient.find(form.getPaymentKey())
                .orElseThrow(() -> new BusinessException(
                        PaymentErrorCode.PAYMENT_STATUS_LOOKUP_FAILED
                ));
        Optional<ApprovalResult> approval = resolveLookup(payment, form, lookup);
        if (approval.isEmpty()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_LOOKUP_FAILED);
        }
        validateApproval(payment, form, approval.get());
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

    private enum ApprovalState {
        NOT_APPROVED,
        IN_PROGRESS,
        DONE,
        CANCELED,
        FINAL_OR_UNKNOWN
    }

    private record ApprovalStateResult(
            ApprovalState state,
            PaymentLookupResult lookup
    ) {
    }
}
