package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.service.OrderService.PaymentProduct;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ProductStockService productStockService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void completeGeneralPayment_validApproval_updatesStockPaymentAndOrderInOrder() {
        GeneralPaymentOrder order = order();
        Payment payment = payment();
        ApprovalResult approval = approval();
        when(paymentMapper.completeIfReady(
                20L,
                "payment-key",
                "카드",
                "DONE",
                approval.approvedAt()
        )).thenReturn(1);

        paymentService.completeGeneralPayment(order, payment, approval);

        InOrder inOrder = inOrder(
                productStockService,
                paymentMapper,
                orderService
        );
        inOrder.verify(productStockService).decreaseStock(100L, 2);
        inOrder.verify(paymentMapper).completeIfReady(
                20L,
                "payment-key",
                "카드",
                "DONE",
                approval.approvedAt()
        );
        inOrder.verify(orderService).completeGeneralOrderAfterPayment(
                1L,
                approval.approvedAt()
        );
    }

    @Test
    void completeGeneralPayment_paymentUpdateFails_doesNotUpdateOrder() {
        GeneralPaymentOrder order = order();
        Payment payment = payment();
        ApprovalResult approval = approval();
        when(paymentMapper.completeIfReady(
                20L,
                "payment-key",
                "카드",
                "DONE",
                approval.approvedAt()
        )).thenReturn(0);

        assertThatThrownBy(() -> paymentService.completeGeneralPayment(
                order,
                payment,
                approval
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_COMPLETE_FAILED)
        );

        verify(productStockService).decreaseStock(100L, 2);
        verify(orderService, never()).completeGeneralOrderAfterPayment(
                1L,
                approval.approvedAt()
        );
    }

    @Test
    void completeGeneralPayment_isTransactional() throws NoSuchMethodException {
        Transactional transactional = PaymentService.class
                .getMethod(
                        "completeGeneralPayment",
                        GeneralPaymentOrder.class,
                        Payment.class,
                        ApprovalResult.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    private GeneralPaymentOrder order() {
        return new GeneralPaymentOrder(
                1L,
                BigDecimal.valueOf(30_000),
                LocalDateTime.of(2026, 8, 1, 10, 10),
                List.of(new PaymentProduct(100L, 2))
        );
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrderId(1L);
        return payment;
    }

    private ApprovalResult approval() {
        return new ApprovalResult(
                "payment-key",
                "ORD-100",
                "카드",
                "DONE",
                30_000L,
                LocalDateTime.of(2026, 8, 1, 10, 1)
        );
    }
}
