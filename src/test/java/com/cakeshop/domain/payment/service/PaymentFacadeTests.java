package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.service.OrderService.PaymentProduct;
import com.cakeshop.domain.payment.dto.form.PaymentConfirmForm;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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
    private PaymentService paymentService;

    @Mock
    private TossPaymentClient tossPaymentClient;

    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentFacade = new PaymentFacade(
                orderService,
                paymentService,
                tossPaymentClient,
                CLOCK
        );
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
