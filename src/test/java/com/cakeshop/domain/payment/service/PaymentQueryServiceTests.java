package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.service.customer.CustomerOrderQueryService;
import com.cakeshop.domain.payment.dto.form.TossPaymentSuccessForm;
import com.cakeshop.domain.payment.dto.view.PaymentCheckoutView;
import com.cakeshop.domain.payment.dto.view.PaymentCompletionView;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTests {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 3, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private CustomerOrderQueryService orderQueryService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private StoreService storeService;

    private PaymentQueryService paymentQueryService;

    @BeforeEach
    void setUp() {
        paymentQueryService = new PaymentQueryService(
                orderQueryService,
                paymentService,
                storeService,
                CLOCK,
                "test-client-key",
                "test-secret-key"
        );
    }

    @Test
    void getCheckout_ownedPendingOrder_returnsActualPaymentView() {
        OrderDetailView order = order(OrderStatus.PENDING_PAYMENT);
        Payment payment = payment(PaymentStatus.READY, null);
        when(orderQueryService.getMemberOrder(10L, 1L)).thenReturn(order);
        when(paymentService.getReadyPayment(1L)).thenReturn(payment);

        PaymentCheckoutView checkout = paymentQueryService.getCheckout(
                10L,
                "member@example.com",
                1L
        );

        assertThat(checkout.orderNumber()).isEqualTo("ORD-100");
        assertThat(checkout.tossOrderId()).isEqualTo("ORD-100");
        assertThat(checkout.orderName()).isEqualTo("딸기 생크림 케이크");
        assertThat(checkout.amount()).isEqualByComparingTo("30000");
        assertThat(checkout.customerEmail()).isEqualTo("member@example.com");
        assertThat(checkout.paymentAvailable()).isTrue();
        assertThat(checkout.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("딸기 생크림 케이크");
            assertThat(item.options()).singleElement().satisfies(option ->
                    assertThat(option.optionName()).isEqualTo("1호")
            );
        });
    }

    @Test
    void getCheckout_productNameOver100Characters_truncatesOrderName() {
        String longProductName = "가".repeat(101);
        when(orderQueryService.getMemberOrder(10L, 1L))
                .thenReturn(order(OrderStatus.PENDING_PAYMENT, longProductName));
        when(paymentService.getReadyPayment(1L)).thenReturn(payment(PaymentStatus.READY, null));

        PaymentCheckoutView checkout = paymentQueryService.getCheckout(
                10L,
                "member@example.com",
                1L
        );

        assertThat(checkout.orderName()).hasSize(100);
    }

    @Test
    void getCheckout_missingSecretKey_disablesPayment() {
        PaymentQueryService serviceWithoutSecretKey = new PaymentQueryService(
                orderQueryService,
                paymentService,
                storeService,
                CLOCK,
                "test-client-key",
                ""
        );
        when(orderQueryService.getMemberOrder(10L, 1L)).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        when(paymentService.getReadyPayment(1L)).thenReturn(payment(PaymentStatus.READY, null));

        PaymentCheckoutView checkout = serviceWithoutSecretKey.getCheckout(10L, "member@example.com", 1L);

        assertThat(checkout.paymentAvailable()).isFalse();
    }

    @Test
    void validateSuccessCallback_completedSamePayment_acceptsCallback() {
        OrderDetailView order = order(OrderStatus.READY_FOR_PICKUP);
        Payment payment = payment(PaymentStatus.DONE, "payment-key");
        when(orderQueryService.getMemberOrder(10L, 1L)).thenReturn(order);
        when(paymentService.findDonePayment(1L)).thenReturn(Optional.of(payment));

        paymentQueryService.validateSuccessCallback(
                10L,
                1L,
                successForm()
        );
    }

    @Test
    void getFailure_providerMessage_returnsOnlyMappedSafeMessage() {
        when(orderQueryService.getMemberOrder(10L, 1L))
                .thenReturn(order(OrderStatus.PENDING_PAYMENT));

        var failure = paymentQueryService.getFailure(
                10L,
                1L,
                "PAY_PROCESS_CANCELED"
        );

        assertThat(failure.retryAvailable()).isTrue();
        assertThat(failure.message()).contains("결제가 취소되었습니다");
    }

    @Test
    void getCompletion_donePayment_returnsActualMethodAndPickupPlace() {
        when(orderQueryService.getMemberOrder(10L, 1L))
                .thenReturn(order(OrderStatus.READY_FOR_PICKUP));
        when(paymentService.getDonePayment(1L))
                .thenReturn(payment(PaymentStatus.DONE, "payment-key"));
        when(storeService.getStoreView()).thenReturn(storeView());

        PaymentCompletionView completion =
                paymentQueryService.getCompletion(10L, 1L);

        assertThat(completion.orderNumber()).isEqualTo("ORD-100");
        assertThat(completion.method()).isEqualTo("카드");
        assertThat(completion.pickupPlace()).isEqualTo("1층 픽업 데스크");
        assertThat(completion.items()).singleElement().satisfies(item ->
                assertThat(item.productName()).isEqualTo("딸기 생크림 케이크")
        );
    }

    private OrderDetailView order(OrderStatus status) {
        return order(status, "딸기 생크림 케이크");
    }

    private OrderDetailView order(OrderStatus status, String productName) {
        return new OrderDetailView(
                1L,
                "ORD-100",
                10L,
                OrderType.GENERAL,
                status,
                "홍길동",
                "010-1111-2222",
                "홍길동",
                "010-1111-2222",
                BigDecimal.valueOf(30_000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(30_000),
                NOW.plusDays(1),
                NOW.plusMinutes(10),
                status == OrderStatus.PENDING_PAYMENT,
                null,
                null,
                null,
                null,
                NOW.minusMinutes(1),
                false,
                List.of(new OrderDetailView.Item(
                        100L,
                        1L,
                        productName,
                        ProductType.GENERAL,
                        1,
                        BigDecimal.valueOf(30_000),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(30_000),
                        null,
                        List.of(new OrderDetailView.Option(
                                "케이크 크기",
                                "1호",
                                BigDecimal.ZERO
                        )),
                        List.of()
                ))
        );
    }

    private Payment payment(PaymentStatus status, String paymentKey) {
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrderId(1L);
        payment.setTossOrderId("ORD-100");
        payment.setPaymentKey(paymentKey);
        payment.setIdempotencyKey("PAY-1");
        payment.setMethod("카드");
        payment.setAmount(BigDecimal.valueOf(30_000));
        payment.setStatus(status);
        return payment;
    }

    private TossPaymentSuccessForm successForm() {
        TossPaymentSuccessForm form = new TossPaymentSuccessForm();
        form.setPaymentKey("payment-key");
        form.setOrderId("ORD-100");
        form.setAmount(BigDecimal.valueOf(30_000));
        return form;
    }

    private StoreView storeView() {
        return new StoreView(
                1L,
                "케이크샵",
                null,
                null,
                "서울시",
                "02-000-0000",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                LocalTime.of(16, 0),
                Set.of(),
                "1층 픽업 데스크",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                30,
                List.of()
        );
    }
}
