package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.OrderOptionValidator.ValidatedOption;
import com.cakeshop.domain.payment.service.PaymentPreparationService;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, 7, 31, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.atZone(TEST_ZONE).toInstant(),
            TEST_ZONE
    );

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private OrderOptionValidator orderOptionValidator;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentPreparationService paymentPreparationService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                productQueryService,
                orderOptionValidator,
                orderMapper,
                paymentPreparationService,
                FIXED_CLOCK
        );
    }

    @Test
    void createGeneralOrderRecalculatesAmountsAndSavesSnapshotsAndReadyPayment() {
        long memberId = 10L;
        ProductSalesInfo product =
                product(
                        1L,
                        ProductType.GENERAL,
                        "딸기 케이크",
                        30_000,
                        2
                );
        when(productQueryService.getSalesInfo(1L)).thenReturn(product);
        when(orderOptionValidator.validate(1L, List.of(101L)))
                .thenReturn(List.of(new ValidatedOption(
                        101L,
                        "크기",
                        "2호",
                        BigDecimal.valueOf(5_000)
                )));
        when(orderMapper.insertOrder(any(Order.class))).thenAnswer(invocation -> {
            invocation.<Order>getArgument(0).setId(100L);
            return 1;
        });
        when(orderMapper.insertOrderItem(any(OrderItem.class))).thenAnswer(invocation -> {
            invocation.<OrderItem>getArgument(0).setId(200L);
            return 1;
        });
        when(orderMapper.insertOrderItemOption(any(OrderItemOption.class))).thenReturn(1);
        GeneralOrderForm form = form(1L, 2, List.of(101L));

        long orderId = orderService.createGeneralOrder(memberId, form);

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
                .isEqualTo(FIXED_NOW.plusMinutes(10));

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

        verify(paymentPreparationService).prepareReadyPayment(
                100L,
                order.getOrderNumber(),
                order.getFinalAmount()
        );

        InOrder saveOrder = inOrder(
                productQueryService,
                orderOptionValidator,
                orderMapper,
                paymentPreparationService
        );
        saveOrder.verify(productQueryService).getSalesInfo(1L);
        saveOrder.verify(orderOptionValidator)
                .validate(1L, List.of(101L));
        saveOrder.verify(orderMapper).insertOrder(any(Order.class));
        saveOrder.verify(orderMapper).insertOrderItem(any(OrderItem.class));
        saveOrder.verify(orderMapper).insertOrderItemOption(any(OrderItemOption.class));
        saveOrder.verify(paymentPreparationService).prepareReadyPayment(
                100L,
                order.getOrderNumber(),
                order.getFinalAmount()
        );
    }

    @Test
    void createGeneralOrderRejectsInvalidMemberIdBeforeProductLookup() {
        assertThatThrownBy(() -> orderService.createGeneralOrder(
                0L,
                form(1L, 1, List.of())
        )).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrderErrorCode.MEMBER_NOT_AVAILABLE)
        );

        verify(productQueryService, never()).getSalesInfo(1L);
        verify(orderMapper, never()).insertOrder(any(Order.class));
        verifyNoInteractions(paymentPreparationService);
    }

    @Test
    void createGeneralOrderRejectsCustomProductBeforeSaving() {
        when(productQueryService.getSalesInfo(2L))
                .thenReturn(product(
                        2L,
                        ProductType.CUSTOM,
                        "주문 제작 케이크",
                        50_000
                ));
        GeneralOrderForm form = form(2L, 1, List.of());

        assertThatThrownBy(() -> orderService.createGeneralOrder(10L, form))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(OrderErrorCode.GENERAL_PRODUCT_REQUIRED)
                );

        verify(orderMapper, never()).insertOrder(any(Order.class));
        verifyNoInteractions(paymentPreparationService);
    }

    @Test
    void createGeneralOrder_inactiveProduct_throwsNotOnSale() {
        when(productQueryService.getSalesInfo(1L))
                .thenThrow(new BusinessException(ProductErrorCode.NOT_ON_SALE));

        assertThatThrownBy(() ->
                orderService.createGeneralOrder(
                        10L,
                        form(1L, 1, List.of())
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(ProductErrorCode.NOT_ON_SALE)
        );

        verify(orderOptionValidator, never()).validate(1L, List.of());
        verify(orderMapper, never()).insertOrder(any(Order.class));
    }

    @Test
    void createGeneralOrder_outOfStockProduct_throwsInsufficientStock() {
        when(productQueryService.getSalesInfo(1L))
                .thenReturn(product(
                        1L,
                        ProductType.GENERAL,
                        "품절 케이크",
                        30_000,
                        0
                ));

        assertThatThrownBy(() ->
                orderService.createGeneralOrder(
                        10L,
                        form(1L, 1, List.of())
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK)
        );

        verify(orderOptionValidator, never()).validate(1L, List.of());
        verify(orderMapper, never()).insertOrder(any(Order.class));
    }

    @Test
    void createGeneralOrder_quantityExceedsStock_throwsInsufficientStock() {
        when(productQueryService.getSalesInfo(1L))
                .thenReturn(product(
                        1L,
                        ProductType.GENERAL,
                        "재고 제한 케이크",
                        30_000,
                        2
                ));

        assertThatThrownBy(() ->
                orderService.createGeneralOrder(
                        10L,
                        form(1L, 3, List.of())
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK)
        );

        verify(orderOptionValidator, never()).validate(1L, List.of());
        verify(orderMapper, never()).insertOrder(any(Order.class));
    }

    @Test
    void createGeneralOrder_pickupAtCurrentTime_throwsInvalidInput() {
        GeneralOrderForm form = form(1L, 1, List.of());
        form.setPickupAt(FIXED_NOW);

        assertThatThrownBy(() ->
                orderService.createGeneralOrder(10L, form)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        verify(productQueryService, never()).getSalesInfo(1L);
        verify(orderMapper, never()).insertOrder(any(Order.class));
    }

    @Test
    void createGeneralOrder_optionValidationFails_doesNotSaveOrder() {
        when(productQueryService.getSalesInfo(1L))
                .thenReturn(product(
                        1L,
                        ProductType.GENERAL,
                        "딸기 케이크",
                        30_000
                ));
        when(orderOptionValidator.validate(1L, List.of(999L)))
                .thenThrow(new BusinessException(
                        OrderErrorCode.INVALID_PRODUCT_OPTION
                ));

        assertThatThrownBy(() ->
                orderService.createGeneralOrder(
                        10L,
                        form(1L, 1, List.of(999L))
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrderErrorCode.INVALID_PRODUCT_OPTION)
        );

        verify(orderMapper, never()).insertOrder(any(Order.class));
        verifyNoInteractions(paymentPreparationService);
    }

    @Test
    void createGeneralOrderIsTransactional() throws NoSuchMethodException {
        Transactional transactional = OrderServiceImpl.class
                .getMethod(
                        "createGeneralOrder",
                        long.class,
                        GeneralOrderForm.class
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
        return product(id, productType, name, basePrice, null);
    }

    private ProductSalesInfo product(
            long id,
            ProductType productType,
            String name,
            long basePrice,
            Integer stockQuantity
    ) {
        return new ProductSalesInfo(
                id,
                name,
                productType,
                0,
                stockQuantity == null || stockQuantity > 0,
                BigDecimal.valueOf(basePrice),
                stockQuantity
        );
    }

    private GeneralOrderForm form(
            long productId,
            int quantity,
            List<Long> optionIds
    ) {
        GeneralOrderForm form = new GeneralOrderForm();
        form.setOrdererName(" 주문자 ");
        form.setOrdererPhone(" 010-1111-2222 ");
        form.setPickupName(" 수령자 ");
        form.setPickupPhone(" 010-3333-4444 ");
        form.setPickupAt(FIXED_NOW.plusDays(3));
        form.setRequestMessage(" 초는 빼주세요. ");
        form.setProductId(productId);
        form.setQuantity(quantity);
        form.setOptionIds(optionIds);
        return form;
    }
}
