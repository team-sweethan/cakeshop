package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentCancellationStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.payment.service.RefundService.RefundRequest;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 15, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ProductStockService productStockService;

    @Mock
    private MemberService memberService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                orderMapper,
                paymentMapper,
                productStockService,
                memberService,
                CLOCK
        );
        lenient().when(memberService.isActiveMember(anyLong())).thenReturn(true);
    }

    @Test
    void prepareCustomerCancellation_inactiveMember_returnsMemberNotAvailable() {
        when(memberService.isActiveMember(3L)).thenReturn(false);

        assertThatThrownBy(() -> refundService.prepareCustomerCancellation(
                3L,
                10L,
                "단순 변심"
        )).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrderErrorCode.MEMBER_NOT_AVAILABLE)
        );

        verify(orderMapper, never()).findOrderByIdForUpdate(10L);
    }

    @Test
    void prepareCustomerCancellation_otherMembersOrder_returnsNotFound() {
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order(99L)));

        assertThatThrownBy(() -> refundService.prepareCustomerCancellation(3L, 10L, "단순 변심"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND)
                );

        verify(paymentMapper, never()).findDonePaymentByOrderId(10L);
    }

    @Test
    void prepareCustomerCancellation_generalBeforePickup_createsFullCancellationRequest() {
        Order order = order(3L);
        Payment payment = payment();
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.insertPaymentCancellation(any(PaymentCancellation.class)))
                .thenAnswer(invocation -> {
                    invocation.<PaymentCancellation>getArgument(0).setId(30L);
                    return 1;
                });

        RefundRequest result = refundService.prepareCustomerCancellation(3L, 10L, " 단순 변심 ");

        assertThat(result.cancellationId()).isEqualTo(30L);
        assertThat(result.expectedStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(result.paymentKey()).isEqualTo("payment-key");
        assertThat(result.reason()).isEqualTo("단순 변심");
        assertThat(result.canceledBy()).isEqualTo("CUSTOMER");
        assertThat(result.requestedAt()).isEqualTo(NOW);
    }

    @Test
    void prepareCustomerCancellation_existingRequestedCancellation_reusesIdempotencyKey() {
        Order order = order(3L);
        Payment payment = payment();
        PaymentCancellation existing = cancellation();
        existing.setIdempotencyKey("existing-cancel-key");
        existing.setRequestType("CUSTOMER");
        existing.setRequestedBy(3L);
        existing.setRequestedAt(NOW.minusMinutes(1));
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.findRequestedCancellationByPaymentId(20L))
                .thenReturn(Optional.of(existing));

        RefundRequest result = refundService.prepareCustomerCancellation(
                3L,
                10L,
                "변경된 취소 사유"
        );

        assertThat(result.cancellationId()).isEqualTo(30L);
        assertThat(result.idempotencyKey()).isEqualTo("existing-cancel-key");
        assertThat(result.reason()).isEqualTo("단순 변심");
        assertThat(result.requestedAt()).isEqualTo(NOW.minusMinutes(1));
        verify(paymentMapper, never()).insertPaymentCancellation(any());
    }

    @Test
    void prepareCustomerCancellation_existingRequestedBeforePickup_reusesRequestAfterPickupTime() {
        Order order = order(3L);
        order.setPickupAt(NOW.minusMinutes(1));
        Payment payment = payment();
        PaymentCancellation existing = cancellation();
        existing.setIdempotencyKey("existing-cancel-key");
        existing.setRequestType("CUSTOMER");
        existing.setRequestedBy(3L);
        existing.setRequestedAt(NOW.minusMinutes(2));
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.findRequestedCancellationByPaymentId(20L))
                .thenReturn(Optional.of(existing));

        RefundRequest result = refundService.prepareCustomerCancellation(3L, 10L, "단순 변심");

        assertThat(result.cancellationId()).isEqualTo(30L);
        assertThat(result.requestedAt()).isEqualTo(NOW.minusMinutes(2));
        verify(paymentMapper, never()).insertPaymentCancellation(any());
    }

    @Test
    void prepareCustomerCancellation_customOrder_isNotSupportedInGeneralMvp() {
        Order order = order(3L);
        order.setOrderType(OrderType.CUSTOM);
        order.setStatus(OrderStatus.UNDER_REVIEW);
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                refundService.prepareCustomerCancellation(3L, 10L, "단순 변심")
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE)
        );

        verify(paymentMapper, never()).findDonePaymentByOrderId(10L);
    }

    @Test
    void prepareAdminCancellation_generalBeforePickup_recordsAdminRequest() {
        Order order = order(3L);
        Payment payment = payment();
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.insertPaymentCancellation(any(PaymentCancellation.class)))
                .thenAnswer(invocation -> {
                    PaymentCancellation saved = invocation.getArgument(0);
                    assertThat(saved.getRequestType()).isEqualTo("ADMIN");
                    assertThat(saved.getRequestedBy()).isEqualTo(7L);
                    saved.setId(31L);
                    return 1;
                });

        RefundRequest result = refundService.prepareAdminCancellation(
                7L,
                10L,
                " 매장 사정 "
        );

        assertThat(result.cancellationId()).isEqualTo(31L);
        assertThat(result.reason()).isEqualTo("매장 사정");
        assertThat(result.canceledBy()).isEqualTo("ADMIN");
        verify(memberService, never()).isActiveMember(7L);
    }

    @Test
    void prepareAdminCancellation_existingCustomerRequest_reusesOriginalRequest() {
        Order order = order(3L);
        Payment payment = payment();
        PaymentCancellation existing = cancellation();
        existing.setIdempotencyKey("existing-cancel-key");
        existing.setRequestType("CUSTOMER");
        existing.setRequestedBy(3L);
        existing.setRequestedAt(NOW.minusMinutes(1));
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.findRequestedCancellationByPaymentId(20L))
                .thenReturn(Optional.of(existing));

        RefundRequest result = refundService.prepareAdminCancellation(
                7L,
                10L,
                "매장 사정"
        );

        assertThat(result.cancellationId()).isEqualTo(30L);
        assertThat(result.idempotencyKey()).isEqualTo("existing-cancel-key");
        assertThat(result.canceledBy()).isEqualTo("CUSTOMER");
        assertThat(result.requestedAt()).isEqualTo(NOW.minusMinutes(1));
        verify(paymentMapper, never()).insertPaymentCancellation(any());
    }

    @Test
    void prepareCustomerCancellation_afterPickup_rejectsAfterCheckingForExistingRequest() {
        Order order = order(3L);
        order.setPickupAt(NOW);
        Payment payment = payment();
        when(orderMapper.findOrderByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.findDonePaymentByOrderId(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.prepareCustomerCancellation(3L, 10L, "단순 변심"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE)
                );

        verify(paymentMapper).findRequestedCancellationByPaymentId(20L);
    }

    @Test
    void prepareCustomerCancellation_reasonOver200_returnsInvalidInput() {
        assertThatThrownBy(() -> refundService.prepareCustomerCancellation(
                3L,
                10L,
                "가".repeat(201)
        )).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        verify(orderMapper, never()).findOrderByIdForUpdate(10L);
    }

    @Test
    void completeCancellation_customer_restoresStockAndCompletesStates() {
        RefundRequest request = request();
        CancellationResult result = new CancellationResult("CANCELED", "transaction-key", NOW.plusSeconds(2));
        PaymentCancellation cancellation = cancellation();
        Order order = order(3L);
        OrderItem item = deductedItem();
        when(paymentMapper.findPaymentCancellationById(30L)).thenReturn(Optional.of(cancellation));
        when(paymentMapper.findPaymentById(20L)).thenReturn(Optional.of(payment()));
        when(orderMapper.findOrderById(10L)).thenReturn(Optional.of(order));
        when(paymentMapper.completeCancellationIfRequested(30L, "transaction-key", NOW.plusSeconds(2)))
                .thenReturn(1);
        when(orderMapper.cancelIfCurrent(
                10L, OrderStatus.READY_FOR_PICKUP, "CUSTOMER", "단순 변심", NOW
        )).thenReturn(1);
        when(orderMapper.findStockDeductedItemsForRestore(10L)).thenReturn(List.of(item));
        when(orderMapper.markStockRestoredIfDeducted(100L, NOW.plusSeconds(2))).thenReturn(1);

        refundService.completeCancellation(request, result);

        verify(productStockService).restoreStock(1L, 2);
        verify(orderMapper).markStockRestoredIfDeducted(100L, NOW.plusSeconds(2));
    }

    @Test
    void completeCancellation_alreadyCompleted_doesNotRestoreStockAgain() {
        PaymentCancellation cancellation = cancellation();
        cancellation.setStatus(PaymentCancellationStatus.DONE);
        when(paymentMapper.findPaymentCancellationById(30L)).thenReturn(Optional.of(cancellation));

        assertThatThrownBy(() -> refundService.completeCancellation(
                request(),
                new CancellationResult("CANCELED", "transaction-key", NOW.plusSeconds(2))
        )).isInstanceOf(BusinessException.class);

        verify(productStockService, never()).restoreStock(1L, 2);
        verify(orderMapper, never()).findStockDeductedItemsForRestore(10L);
    }

    @Test
    void completeCancellation_admin_recordsAdminActor() {
        RefundRequest request = request("ADMIN");
        CancellationResult result = new CancellationResult(
                "CANCELED",
                "transaction-key",
                NOW.plusSeconds(2)
        );
        when(paymentMapper.findPaymentCancellationById(30L))
                .thenReturn(Optional.of(cancellation()));
        when(paymentMapper.findPaymentById(20L)).thenReturn(Optional.of(payment()));
        when(orderMapper.findOrderById(10L)).thenReturn(Optional.of(order(3L)));
        when(paymentMapper.completeCancellationIfRequested(
                30L,
                "transaction-key",
                NOW.plusSeconds(2)
        )).thenReturn(1);
        when(orderMapper.cancelIfCurrent(
                10L,
                OrderStatus.READY_FOR_PICKUP,
                "ADMIN",
                "단순 변심",
                NOW
        )).thenReturn(1);
        when(orderMapper.findStockDeductedItemsForRestore(10L)).thenReturn(List.of());

        refundService.completeCancellation(request, result);

        verify(orderMapper).cancelIfCurrent(
                10L,
                OrderStatus.READY_FOR_PICKUP,
                "ADMIN",
                "단순 변심",
                NOW
        );
    }

    private Order order(long memberId) {
        Order order = new Order();
        order.setId(10L);
        order.setMemberId(memberId);
        order.setOrderType(OrderType.GENERAL);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setPickupAt(NOW.plusHours(1));
        return order;
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrderId(10L);
        payment.setPaymentKey("payment-key");
        payment.setAmount(BigDecimal.valueOf(40_000));
        return payment;
    }

    private PaymentCancellation cancellation() {
        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.setId(30L);
        cancellation.setPaymentId(20L);
        cancellation.setCancelReason("단순 변심");
        cancellation.setStatus(PaymentCancellationStatus.REQUESTED);
        return cancellation;
    }

    private OrderItem deductedItem() {
        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrderId(10L);
        item.setProductId(1L);
        item.setProductType(ProductType.GENERAL);
        item.setQuantity(2);
        item.setStockDeductedAt(NOW.minusDays(1));
        return item;
    }

    private RefundRequest request() {
        return request("CUSTOMER");
    }

    private RefundRequest request(String canceledBy) {
        return new RefundRequest(
                30L,
                10L,
                OrderStatus.READY_FOR_PICKUP,
                "payment-key",
                "idempotency-key",
                "단순 변심",
                canceledBy,
                NOW
        );
    }
}
