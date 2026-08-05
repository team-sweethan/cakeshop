package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.customer.CustomerOrderQueryService;
import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.service.OrderService.PaymentProduct;
import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.PaymentLookupResult;
import com.cakeshop.domain.payment.service.PaymentRecoveryService.CompensationRequest;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTests {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderService orderService;

    @Mock
    private CustomerOrderQueryService orderQueryService;

    @Mock
    private OrderDetailView ownedOrder;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentRecoveryService paymentRecoveryService;

    @Mock
    private TossPaymentClient tossPaymentClient;

    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentFacade = new PaymentFacade(
                orderService,
                orderQueryService,
                paymentService,
                paymentRecoveryService,
                tossPaymentClient,
                CLOCK
        );
        lenient().when(orderQueryService.getMemberOrder(10L, 1L))
                .thenReturn(ownedOrder);
        lenient().when(ownedOrder.orderNumber()).thenReturn("ORD-100");
        lenient().when(ownedOrder.finalAmount())
                .thenReturn(BigDecimal.valueOf(30_000));
    }

    @Test
    void confirmGeneralPayment_validRequest_approvesAndCompletesPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        ApprovalResult approval = approval();

        when(orderService.getGeneralPaymentOrder(10L, 1L))
                .thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);

        paymentFacade.confirmGeneralPayment(10L, 1L, form);

        verify(paymentService).completeGeneralPayment(
                order,
                payment,
                approval
        );
    }

    @Test
    void confirmGeneralPayment_amountMismatch_doesNotCallToss() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(29_000));

        when(orderService.getGeneralPaymentOrder(10L, 1L))
                .thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form
                ),
                PaymentErrorCode.AMOUNT_MISMATCH
        );

        verify(tossPaymentClient, never()).approve(
                "payment-key",
                "ORD-100",
                29_000L,
                "PAY-1"
        );
        verify(paymentService, never()).completeGeneralPayment(
                order,
                payment,
                approval()
        );
    }

    @Test
    void confirmGeneralPayment_expiredOrder_doesNotLoadPayment() {
        GeneralPaymentOrder order = order(NOW);

        when(orderService.getGeneralPaymentOrder(10L, 1L))
                .thenReturn(order);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form(BigDecimal.valueOf(30_000))
                ),
                PaymentErrorCode.PAYMENT_EXPIRED
        );

        verify(paymentService, never()).getReadyPayment(1L);
        verify(tossPaymentClient, never()).approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        );
    }

    @Test
    void confirmGeneralPayment_tossOrderIdMismatch_doesNotCallToss() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        form.setTossOrderId("ORD-OTHER");

        when(orderService.getGeneralPaymentOrder(10L, 1L))
                .thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form
                ),
                PaymentErrorCode.TOSS_ORDER_ID_MISMATCH
        );

        verify(tossPaymentClient, never()).approve(
                "payment-key",
                "ORD-OTHER",
                30_000L,
                "PAY-1"
        );
    }

    @Test
    void confirmGeneralPayment_approvalPaymentKeyMismatch_doesNotCompletePayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        ApprovalResult mismatchedApproval = new ApprovalResult(
                "other-payment-key",
                "ORD-100",
                "카드",
                "DONE",
                30_000L,
                NOW.plusSeconds(10)
        );

        when(orderService.getGeneralPaymentOrder(10L, 1L))
                .thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(mismatchedApproval);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form
                ),
                PaymentErrorCode.TOSS_APPROVAL_FAILED
        );

        verify(paymentService, never()).completeGeneralPayment(
                order,
                payment,
                mismatchedApproval
        );
    }

    @Test
    void confirmGeneralPayment_sameCompletedCallback_returnsWithoutTossRetry() {
        Payment completedPayment = payment();
        completedPayment.setStatus(PaymentStatus.DONE);
        completedPayment.setPaymentKey("payment-key");
        PaymentLookupResult lookup = new PaymentLookupResult(
                "payment-key",
                "ORD-100",
                "카드",
                "DONE",
                30_000L,
                NOW.plusSeconds(10),
                null
        );
        when(paymentService.findDonePayment(1L))
                .thenReturn(Optional.of(completedPayment));
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.of(lookup));

        paymentFacade.confirmGeneralPayment(
                10L,
                1L,
                form(BigDecimal.valueOf(30_000))
        );

        verify(orderService, never()).getGeneralPaymentOrder(10L, 1L);
        verify(tossPaymentClient, never()).approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        );
    }

    @Test
    void confirmGeneralPayment_localDoneButProviderCanceled_completesCompensation() {
        Payment completedPayment = payment();
        completedPayment.setStatus(PaymentStatus.DONE);
        completedPayment.setPaymentKey("payment-key");
        CompensationRequest request = compensationRequest();
        CancellationResult cancellation = cancellation();
        PaymentLookupResult lookup = new PaymentLookupResult(
                "payment-key",
                "ORD-100",
                "카드",
                "CANCELED",
                30_000L,
                NOW.plusSeconds(10),
                cancellation
        );
        when(paymentService.findDonePayment(1L))
                .thenReturn(Optional.of(completedPayment));
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.of(lookup));
        when(paymentRecoveryService.createRequest(completedPayment, "payment-key"))
                .thenReturn(request);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form(BigDecimal.valueOf(30_000))
                ),
                PaymentErrorCode.PAYMENT_COMPENSATED
        );

        verify(paymentRecoveryService).prepareCompensation(request);
        verify(paymentRecoveryService).completeCompensation(request, cancellation);
    }

    @Test
    void confirmGeneralPayment_approvalTimeout_lookupDone_completesPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        PaymentLookupResult lookup = new PaymentLookupResult(
                "payment-key",
                "ORD-100",
                "카드",
                "DONE",
                30_000L,
                NOW.plusSeconds(10),
                null
        );
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(lookup));
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenThrow(new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED));

        paymentFacade.confirmGeneralPayment(10L, 1L, form);

        verify(paymentService).completeGeneralPayment(
                order,
                payment,
                lookup.toApprovalResult()
        );
    }

    @Test
    void confirmGeneralPayment_approvalAndLookupFailure_persistsRecoveryTarget() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        CompensationRequest request = compensationRequest();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.empty())
                .thenThrow(new BusinessException(
                        PaymentErrorCode.PAYMENT_STATUS_LOOKUP_FAILED
                ));
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenThrow(new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED));
        when(paymentRecoveryService.createRequest(payment, "payment-key"))
                .thenReturn(request);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(10L, 1L, form),
                PaymentErrorCode.PAYMENT_RECOVERY_PENDING
        );

        verify(paymentRecoveryService).prepareCompensation(request);
        verify(paymentService, never()).completeGeneralPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void confirmGeneralPayment_approvalFailureLookupEmpty_persistsRecoveryTarget() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        CompensationRequest request = compensationRequest();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.find("payment-key")).thenReturn(Optional.empty());
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenThrow(new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED));
        when(paymentRecoveryService.createRequest(payment, "payment-key"))
                .thenReturn(request);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form(BigDecimal.valueOf(30_000))
                ),
                PaymentErrorCode.PAYMENT_RECOVERY_PENDING
        );

        verify(paymentRecoveryService).prepareCompensation(request);
    }

    @Test
    void confirmGeneralPayment_lookupInProgress_usesSameIdempotentApproval() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        ApprovalResult approval = approval();
        PaymentLookupResult inProgress = new PaymentLookupResult(
                "payment-key",
                "ORD-100",
                null,
                "IN_PROGRESS",
                30_000L,
                null,
                null
        );
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.of(inProgress));
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);

        paymentFacade.confirmGeneralPayment(
                10L,
                1L,
                form(BigDecimal.valueOf(30_000))
        );

        verify(paymentService).completeGeneralPayment(order, payment, approval);
    }

    @Test
    void confirmGeneralPayment_internalFailure_cancelsApprovedPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        PaymentConfirmForm form = form(BigDecimal.valueOf(30_000));
        ApprovalResult approval = approval();
        CompensationRequest request = new CompensationRequest(
                20L,
                1L,
                "payment-key",
                "COMPENSATE-20",
                BigDecimal.valueOf(30_000),
                "내부 주문 처리 실패로 인한 자동 결제 취소"
        );
        CancellationResult cancellation = new CancellationResult(
                "CANCELED",
                "cancel-transaction",
                NOW.plusSeconds(20)
        );
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);
        org.mockito.Mockito.doThrow(
                new BusinessException(PaymentErrorCode.PAYMENT_COMPLETE_FAILED)
        ).when(paymentService).completeGeneralPayment(order, payment, approval);
        when(paymentRecoveryService.createRequest(payment, "payment-key"))
                .thenReturn(request);
        when(tossPaymentClient.cancel(
                "payment-key",
                request.reason(),
                "COMPENSATE-20"
        )).thenReturn(cancellation);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(10L, 1L, form),
                PaymentErrorCode.PAYMENT_COMPENSATED
        );

        verify(paymentRecoveryService).prepareCompensation(request);
        verify(paymentRecoveryService).completeCompensation(request, cancellation);
    }

    @Test
    void confirmGeneralPayment_compensationPrepareFailure_doesNotCancelWithoutRecoveryRecord() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        ApprovalResult approval = approval();
        CompensationRequest request = compensationRequest();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.approve("payment-key", "ORD-100", 30_000L, "PAY-1"))
                .thenReturn(approval);
        org.mockito.Mockito.doThrow(new BusinessException(PaymentErrorCode.PAYMENT_COMPLETE_FAILED))
                .when(paymentService).completeGeneralPayment(order, payment, approval);
        when(paymentRecoveryService.createRequest(payment, "payment-key")).thenReturn(request);
        org.mockito.Mockito.doThrow(new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING))
                .when(paymentRecoveryService).prepareCompensation(request);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(10L, 1L, form(BigDecimal.valueOf(30_000))),
                PaymentErrorCode.PAYMENT_RECOVERY_PENDING
        );

        verify(tossPaymentClient, never()).cancel(
                "payment-key",
                request.reason(),
                request.idempotencyKey()
        );
    }

    @Test
    void confirmGeneralPayment_concurrentCompletion_doesNotCancelPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment readyPayment = payment();
        Payment donePayment = payment();
        donePayment.setStatus(PaymentStatus.DONE);
        donePayment.setPaymentKey("payment-key");
        ApprovalResult approval = approval();
        when(paymentService.findDonePayment(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(donePayment));
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(readyPayment);
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);
        org.mockito.Mockito.doThrow(
                new BusinessException(PaymentErrorCode.PAYMENT_COMPLETE_FAILED)
        ).when(paymentService).completeGeneralPayment(order, readyPayment, approval);

        paymentFacade.confirmGeneralPayment(
                10L,
                1L,
                form(BigDecimal.valueOf(30_000))
        );

        verify(tossPaymentClient, never()).cancel(
                "payment-key",
                "내부 주문 처리 실패로 인한 자동 결제 취소",
                "COMPENSATE-20"
        );
    }

    @Test
    void confirmGeneralPayment_cancelTimeout_lookupCanceled_completesRecovery() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        ApprovalResult approval = approval();
        CompensationRequest request = compensationRequest();
        CancellationResult cancellation = cancellation();
        PaymentLookupResult canceledLookup = new PaymentLookupResult(
                "payment-key",
                "ORD-100",
                "카드",
                "CANCELED",
                30_000L,
                NOW.plusSeconds(10),
                cancellation
        );
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(tossPaymentClient.find("payment-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(canceledLookup));
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);
        org.mockito.Mockito.doThrow(
                new BusinessException(PaymentErrorCode.PAYMENT_COMPLETE_FAILED)
        ).when(paymentService).completeGeneralPayment(order, payment, approval);
        when(paymentRecoveryService.createRequest(payment, "payment-key"))
                .thenReturn(request);
        when(tossPaymentClient.cancel(
                request.paymentKey(),
                request.reason(),
                request.idempotencyKey()
        )).thenThrow(new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED));

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form(BigDecimal.valueOf(30_000))
                ),
                PaymentErrorCode.PAYMENT_COMPENSATED
        );

        verify(paymentRecoveryService).completeCompensation(request, cancellation);
    }

    @Test
    void confirmGeneralPayment_preparedCompensation_resumesCancelWithoutApproval() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        CompensationRequest request = compensationRequest();
        CancellationResult cancellation = cancellation();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);
        when(paymentRecoveryService.findPreparedCompensation(payment))
                .thenReturn(Optional.of(request));
        when(tossPaymentClient.find(request.paymentKey())).thenReturn(Optional.of(
                new PaymentLookupResult(
                        request.paymentKey(),
                        "ORD-100",
                        "카드",
                        "DONE",
                        30_000L,
                        NOW,
                        null
                )
        ));
        when(tossPaymentClient.cancel(
                request.paymentKey(),
                request.reason(),
                request.idempotencyKey()
        )).thenReturn(cancellation);

        assertPaymentError(
                () -> paymentFacade.confirmGeneralPayment(
                        10L,
                        1L,
                        form(BigDecimal.valueOf(30_000))
                ),
                PaymentErrorCode.PAYMENT_COMPENSATED
        );

        verify(tossPaymentClient, never()).approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        );
        verify(paymentRecoveryService).completeCompensation(request, cancellation);
    }

    @Test
    void confirmGeneralPayment_unapprovedPreparedCompensation_releasesAndApprovesNewPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        payment.setPaymentKey("stale-payment-key");
        Payment refreshedPayment = payment();
        refreshedPayment.setIdempotencyKey("PAY-RETRY");
        CompensationRequest request = new CompensationRequest(
                20L,
                1L,
                "stale-payment-key",
                "COMPENSATE-20",
                BigDecimal.valueOf(30_000),
                "자동 취소"
        );
        ApprovalResult approval = approval();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment, refreshedPayment);
        when(paymentRecoveryService.findPreparedCompensation(payment))
                .thenReturn(Optional.of(request));
        when(tossPaymentClient.find("stale-payment-key")).thenReturn(Optional.empty());
        when(tossPaymentClient.find("payment-key")).thenReturn(Optional.empty());
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-RETRY"
        )).thenReturn(approval);

        paymentFacade.confirmGeneralPayment(10L, 1L, form(BigDecimal.valueOf(30_000)));

        verify(paymentRecoveryService).releaseUnapprovedCompensation(request);
        verify(paymentService).completeGeneralPayment(order, refreshedPayment, approval);
    }

    @Test
    void confirmGeneralPayment_inProgressPreparedCompensation_releasesAndApprovesNewPayment() {
        GeneralPaymentOrder order = order(NOW.plusMinutes(5));
        Payment payment = payment();
        payment.setPaymentKey("stale-payment-key");
        Payment refreshedPayment = payment();
        CompensationRequest request = new CompensationRequest(
                20L,
                1L,
                "stale-payment-key",
                "COMPENSATE-20",
                BigDecimal.valueOf(30_000),
                "자동 취소"
        );
        ApprovalResult approval = approval();
        when(orderService.getGeneralPaymentOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment, refreshedPayment);
        when(paymentRecoveryService.findPreparedCompensation(payment))
                .thenReturn(Optional.of(request));
        when(tossPaymentClient.find("stale-payment-key")).thenReturn(Optional.of(
                new PaymentLookupResult(
                        "stale-payment-key",
                        "ORD-100",
                        "카드",
                        "IN_PROGRESS",
                        30_000L,
                        null,
                        null
                )
        ));
        when(tossPaymentClient.find("payment-key")).thenReturn(Optional.empty());
        when(tossPaymentClient.approve(
                "payment-key",
                "ORD-100",
                30_000L,
                "PAY-1"
        )).thenReturn(approval);

        paymentFacade.confirmGeneralPayment(10L, 1L, form(BigDecimal.valueOf(30_000)));

        verify(paymentRecoveryService).releaseUnapprovedCompensation(request);
        verify(paymentService).completeGeneralPayment(order, refreshedPayment, approval);
    }

    @Test
    void recoverPendingCompensations_persistedRequest_retriesSameCancelKey() {
        CompensationRequest request = compensationRequest();
        CancellationResult cancellation = cancellation();
        when(paymentRecoveryService.getPreparedCompensations(50))
                .thenReturn(List.of(request));
        when(tossPaymentClient.find(request.paymentKey())).thenReturn(Optional.of(
                new PaymentLookupResult(
                        request.paymentKey(),
                        "ORD-100",
                        "카드",
                        "DONE",
                        30_000L,
                        NOW,
                        null
                )
        ));
        when(tossPaymentClient.cancel(
                request.paymentKey(),
                request.reason(),
                request.idempotencyKey()
        )).thenReturn(cancellation);

        paymentFacade.recoverPendingCompensations(50);

        verify(paymentRecoveryService).completeCompensation(request, cancellation);
    }

    @Test
    void recoverPendingCompensations_unapprovedRequest_releasesCompensation() {
        CompensationRequest request = compensationRequest();
        when(paymentRecoveryService.getPreparedCompensations(50))
                .thenReturn(List.of(request));
        when(tossPaymentClient.find(request.paymentKey())).thenReturn(Optional.empty());

        paymentFacade.recoverPendingCompensations(50);

        verify(paymentRecoveryService).releaseUnapprovedCompensation(request);
        verify(tossPaymentClient, never()).cancel(
                request.paymentKey(),
                request.reason(),
                request.idempotencyKey()
        );
    }

    private GeneralPaymentOrder order(LocalDateTime expiresAt) {
        return new GeneralPaymentOrder(
                1L,
                BigDecimal.valueOf(30_000),
                expiresAt,
                List.of(new PaymentProduct(200L, 100L, 2))
        );
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrderId(1L);
        payment.setTossOrderId("ORD-100");
        payment.setIdempotencyKey("PAY-1");
        payment.setAmount(BigDecimal.valueOf(30_000));
        payment.setStatus(PaymentStatus.READY);
        return payment;
    }

    private PaymentConfirmForm form(BigDecimal amount) {
        PaymentConfirmForm form = new PaymentConfirmForm();
        form.setPaymentKey("payment-key");
        form.setTossOrderId("ORD-100");
        form.setAmount(amount);
        return form;
    }

    private ApprovalResult approval() {
        return new ApprovalResult(
                "payment-key",
                "ORD-100",
                "카드",
                "DONE",
                30_000L,
                NOW.plusSeconds(10)
        );
    }

    private CompensationRequest compensationRequest() {
        return new CompensationRequest(
                20L,
                1L,
                "payment-key",
                "COMPENSATE-20",
                BigDecimal.valueOf(30_000),
                "내부 주문 처리 실패로 인한 자동 결제 취소"
        );
    }

    private CancellationResult cancellation() {
        return new CancellationResult(
                "CANCELED",
                "cancel-transaction",
                NOW.plusSeconds(20)
        );
    }

    private void assertPaymentError(
            Runnable action,
            PaymentErrorCode expected
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}
