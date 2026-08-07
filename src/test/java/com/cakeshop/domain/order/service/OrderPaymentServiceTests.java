package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.payment.service.PaymentPreparationService;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
class OrderPaymentServiceTests {

    @Mock
    private StoreService storeService;

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private OrderOptionValidator orderOptionValidator;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentPreparationService paymentPreparationService;

    @Mock
    private MemberService memberService;

    @Mock
    private CouponOrderCommandService couponOrderCommandService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(memberService.isActiveMember(10L)).thenReturn(true);
        orderService = new OrderServiceImpl(
                storeService,
                productQueryService,
                orderOptionValidator,
                orderMapper,
                paymentPreparationService,
                memberService,
                couponOrderCommandService,
                Clock.fixed(
                        Instant.parse("2026-08-01T01:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    @Test
    void getGeneralPaymentOrder_ownedPendingOrder_returnsPaymentTarget() {
        Order order = order(10L);
        OrderItem item = item();
        when(orderMapper.findOrderById(1L))
                .thenReturn(Optional.of(order));
        when(orderMapper.findOrderItemsByOrderId(1L))
                .thenReturn(List.of(item));

        GeneralPaymentOrder result =
                orderService.getGeneralPaymentOrder(10L, 1L);

        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.amount()).isEqualByComparingTo("30000");
        assertThat(result.products()).singleElement()
                .satisfies(product -> {
                    assertThat(product.orderItemId()).isEqualTo(200L);
                    assertThat(product.productId()).isEqualTo(100L);
                    assertThat(product.quantity()).isEqualTo(2);
                });
    }

    @Test
    void getGeneralPaymentOrder_otherMembersOrder_throwsNotFound() {
        when(orderMapper.findOrderById(1L))
                .thenReturn(Optional.of(order(99L)));

        assertThatThrownBy(() ->
                orderService.getGeneralPaymentOrder(10L, 1L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND)
        );

        verify(orderMapper, never()).findOrderItemsByOrderId(1L);
    }

    @Test
    void completeGeneralOrderAfterPayment_donePayment_marksReady() {
        LocalDateTime approvedAt =
                LocalDateTime.of(2026, 8, 1, 10, 1);
        when(orderMapper.markReadyForPickupAfterPaymentIfPending(
                1L,
                approvedAt
        )).thenReturn(1);

        orderService.completeGeneralOrderAfterPayment(1L, approvedAt);

        verify(orderMapper).markReadyForPickupAfterPaymentIfPending(
                1L,
                approvedAt
        );
    }

    @Test
    void lockGeneralOrderForPayment_pendingGeneralOrder_locksOrderRow() {
        when(orderMapper.findOrderByIdForUpdate(1L))
                .thenReturn(Optional.of(order(10L)));

        orderService.lockGeneralOrderForPayment(1L);

        verify(orderMapper).findOrderByIdForUpdate(1L);
    }

    @Test
    void lockGeneralOrderForPayment_expiredOrder_rejectsCompletion() {
        Order expired = order(10L);
        expired.setStatus(OrderStatus.EXPIRED);
        when(orderMapper.findOrderByIdForUpdate(1L))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> orderService.lockGeneralOrderForPayment(1L))
                .isInstanceOf(BusinessException.class);
    }

    private Order order(long memberId) {
        Order order = new Order();
        order.setId(1L);
        order.setMemberId(memberId);
        order.setOrderType(OrderType.GENERAL);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setFinalAmount(BigDecimal.valueOf(30_000));
        order.setPaymentExpiresAt(
                LocalDateTime.of(2026, 8, 1, 10, 10)
        );
        return order;
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setId(200L);
        item.setProductId(100L);
        item.setProductType(ProductType.GENERAL);
        item.setQuantity(2);
        return item;
    }
}
