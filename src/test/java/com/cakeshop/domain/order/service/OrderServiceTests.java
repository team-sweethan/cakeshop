package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.CreateOrderForm;
import com.cakeshop.domain.order.dto.form.OrderItemForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionItemView;
import com.cakeshop.domain.product.customer.service.ProductService;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private ProductService productService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentMapper paymentMapper;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                productQueryService,
                productService,
                orderMapper,
                paymentMapper
        );
    }

    @Test
    void createGeneralOrderRecalculatesAmountsAndSavesSnapshotsAndReadyPayment() {
        long memberId = 10L;
        ProductSalesInfo product =
                product(1L, ProductType.GENERAL, "딸기 케이크", 30_000);
        ProductOptionGroupView optionGroup = new ProductOptionGroupView(
                11L,
                "크기",
                true,
                "SINGLE",
                List.of(new ProductOptionItemView(
                        101L,
                        "2호",
                        BigDecimal.valueOf(5_000)
                ))
        );
        when(productQueryService.getSalesInfo(1L)).thenReturn(product);
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup));
        when(orderMapper.insertOrder(any(Order.class))).thenAnswer(invocation -> {
            invocation.<Order>getArgument(0).setId(100L);
            return 1;
        });
        when(orderMapper.insertOrderItem(any(OrderItem.class))).thenAnswer(invocation -> {
            invocation.<OrderItem>getArgument(0).setId(200L);
            return 1;
        });
        when(orderMapper.insertOrderItemOption(any(OrderItemOption.class))).thenReturn(1);
        when(paymentMapper.insertReadyPayment(any(Payment.class))).thenReturn(1);
        CreateOrderForm form = form(item(1L, 2, List.of(101L)));
        LocalDateTime beforeCreation = LocalDateTime.now();

        long orderId = orderService.createGeneralOrder(memberId, form);

        LocalDateTime afterCreation = LocalDateTime.now();
        assertThat(orderId).isEqualTo(100L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insertOrder(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertThat(order.getMemberId()).isEqualTo(memberId);
        assertThat(order.getOrderType()).isEqualTo(OrderType.GENERAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getOriginalAmount()).isEqualByComparingTo("70000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getFinalAmount()).isEqualByComparingTo("70000");
        assertThat(order.getPaymentExpiresAt())
                .isBetween(
                        beforeCreation.plusMinutes(10),
                        afterCreation.plusMinutes(10)
                );

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderMapper).insertOrderItem(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getProductName()).isEqualTo("딸기 케이크");
        assertThat(savedItem.getProductType()).isEqualTo(ProductType.GENERAL);
        assertThat(savedItem.getQuantity()).isEqualTo(2);
        assertThat(savedItem.getBasePrice()).isEqualByComparingTo("30000");
        assertThat(savedItem.getOptionAmount()).isEqualByComparingTo("5000");
        assertThat(savedItem.getTotalAmount()).isEqualByComparingTo("70000");

        ArgumentCaptor<OrderItemOption> optionCaptor =
                ArgumentCaptor.forClass(OrderItemOption.class);
        verify(orderMapper).insertOrderItemOption(optionCaptor.capture());
        assertThat(optionCaptor.getValue()).satisfies(snapshot -> {
            assertThat(snapshot.getOrderItemId()).isEqualTo(200L);
            assertThat(snapshot.getProductOptionId()).isEqualTo(101L);
            assertThat(snapshot.getOptionGroupName()).isEqualTo("크기");
            assertThat(snapshot.getOptionName()).isEqualTo("2호");
            assertThat(snapshot.getAdditionalPrice()).isEqualByComparingTo("5000");
        });

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertReadyPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue()).satisfies(payment -> {
            assertThat(payment.getOrderId()).isEqualTo(100L);
            assertThat(payment.getAmount()).isEqualByComparingTo("70000");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getTossOrderId()).isEqualTo(order.getOrderNumber());
            assertThat(payment.getIdempotencyKey()).startsWith("PAY-");
        });

        InOrder saveOrder = inOrder(
                productQueryService,
                productService,
                orderMapper,
                paymentMapper
        );
        saveOrder.verify(productQueryService).getSalesInfo(1L);
        saveOrder.verify(productService).getPublicOptionGroups(1L);
        saveOrder.verify(orderMapper).insertOrder(any(Order.class));
        saveOrder.verify(orderMapper).insertOrderItem(any(OrderItem.class));
        saveOrder.verify(orderMapper).insertOrderItemOption(any(OrderItemOption.class));
        saveOrder.verify(paymentMapper).insertReadyPayment(any(Payment.class));
    }

    @Test
    void createGeneralOrderRejectsInvalidMemberIdBeforeProductLookup() {
        assertThatThrownBy(() -> orderService.createGeneralOrder(
                0L,
                form(item(1L, 1, List.of()))
        )).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrderErrorCode.MEMBER_NOT_AVAILABLE)
        );

        verify(productQueryService, never()).getSalesInfo(1L);
        verify(orderMapper, never()).insertOrder(any(Order.class));
        verify(paymentMapper, never()).insertReadyPayment(any(Payment.class));
    }

    @Test
    void createGeneralOrderRejectsMixedGeneralAndCustomProductsBeforeSaving() {
        when(productQueryService.getSalesInfo(1L))
                .thenReturn(product(
                        1L,
                        ProductType.GENERAL,
                        "일반 케이크",
                        30_000
                ));
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of());
        when(productQueryService.getSalesInfo(2L))
                .thenReturn(product(
                        2L,
                        ProductType.CUSTOM,
                        "주문 제작 케이크",
                        50_000
                ));
        CreateOrderForm form = form(
                item(1L, 1, List.of()),
                item(2L, 1, List.of())
        );

        assertThatThrownBy(() -> orderService.createGeneralOrder(10L, form))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(OrderErrorCode.GENERAL_PRODUCT_REQUIRED)
                );

        verify(orderMapper, never()).insertOrder(any(Order.class));
        verify(paymentMapper, never()).insertReadyPayment(any(Payment.class));
    }

    @Test
    void createGeneralOrderIsTransactional() throws NoSuchMethodException {
        Transactional transactional = OrderService.class
                .getMethod(
                        "createGeneralOrder",
                        long.class,
                        CreateOrderForm.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    private ProductSalesInfo product(
            long id,
            ProductType productType,
            String name,
            long basePrice
    ) {
        return new ProductSalesInfo(
                id,
                name,
                productType,
                0,
                true,
                BigDecimal.valueOf(basePrice),
                null
        );
    }

    private CreateOrderForm form(OrderItemForm... items) {
        CreateOrderForm form = new CreateOrderForm();
        form.setOrdererName(" 주문자 ");
        form.setOrdererPhone(" 010-1111-2222 ");
        form.setPickupName(" 수령자 ");
        form.setPickupPhone(" 010-3333-4444 ");
        form.setPickupAt(LocalDateTime.now().plusDays(3));
        form.setRequestMessage(" 초는 빼주세요. ");
        form.setItems(List.of(items));
        return form;
    }

    private OrderItemForm item(
            long productId,
            int quantity,
            List<Long> optionIds
    ) {
        OrderItemForm item = new OrderItemForm();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setOptionIds(optionIds);
        return item;
    }
}
