package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.OrderOptionValidator;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.OrderServiceImpl;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.service.OrderService.PaymentProduct;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Import({
        PaymentService.class,
        OrderServiceImpl.class,
        PaymentPreparationServiceImpl.class,
        ProductStockService.class
})
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PaymentCompletionIntegrationTests {

    private static final LocalDateTime APPROVED_AT =
            LocalDateTime.of(2026, 8, 1, 12, 0);

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private OrderOptionValidator orderOptionValidator;

    @MockitoBean
    private StoreService storeService;

    @MockitoBean
    private Clock clock;

    private String suffix;
    private long memberId;
    private long productId;
    private long orderId;

    @Autowired
    PaymentCompletionIntegrationTests(
            PaymentService paymentService,
            PaymentMapper paymentMapper,
            OrderMapper orderMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
        orderId = insertOrder();
        insertOrderItem();
        insertReadyPayment();
    }

    @Test
    void completeGeneralPayment_readyPayment_updatesStockPaymentAndOrder() {
        assertThat(AopUtils.isAopProxy(paymentService)).isTrue();
        Payment readyPayment = paymentService.getReadyPayment(orderId);
        GeneralPaymentOrder order = new GeneralPaymentOrder(
                orderId,
                BigDecimal.valueOf(40_000),
                APPROVED_AT.plusMinutes(10),
                List.of(new PaymentProduct(productId, 2))
        );
        ApprovalResult approval = new ApprovalResult(
                "PAYMENT-KEY-" + suffix,
                readyPayment.getTossOrderId(),
                "CARD",
                "DONE",
                40_000,
                APPROVED_AT
        );

        paymentService.completeGeneralPayment(order, readyPayment, approval);

        Integer stockQuantity = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class,
                productId
        );
        assertThat(stockQuantity).isEqualTo(3);

        Payment completedPayment = paymentMapper
                .findPaymentsByOrderId(orderId)
                .getFirst();
        assertThat(completedPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(completedPayment.getPaymentKey())
                .isEqualTo(approval.paymentKey());
        assertThat(completedPayment.getMethod()).isEqualTo("CARD");
        assertThat(completedPayment.getProviderStatus()).isEqualTo("DONE");
        assertThat(completedPayment.getApprovedAt()).isEqualTo(APPROVED_AT);

        Order completedOrder = orderMapper.findOrderById(orderId).orElseThrow();
        assertThat(completedOrder.getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(completedOrder.getReadyAt()).isEqualTo(APPROVED_AT);
    }

    private long insertMember() {
        String email = "payment-completion-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email,
                    password,
                    name,
                    nickname,
                    phone,
                    role,
                    status
                )
                VALUES (?, NULL, ?, ?, ?, 'USER', 'ACTIVE')
                """,
                email,
                "결제 완료 테스트 회원",
                "결제완료테스트",
                "010-0000-0000"
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private long insertProduct() {
        String categoryCode = "PAYMENT_COMPLETION_" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO categories (
                    code,
                    name,
                    sort_order,
                    is_active
                )
                VALUES (?, ?, 999, 1)
                """,
                categoryCode,
                "결제 완료 테스트"
        );
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        String productName = "결제 완료 테스트 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    description,
                    base_price,
                    product_type,
                    preparation_days,
                    stock_quantity,
                    status
                )
                VALUES (?, ?, '', 20000, 'GENERAL', 0, 5, 'ACTIVE')
                """,
                categoryId,
                productName
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                productName
        );
    }

    private long insertOrder() {
        String orderNumber = "PAYMENT-COMPLETION-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number,
                    member_id,
                    order_type,
                    orderer_name,
                    orderer_phone,
                    pickup_name,
                    pickup_phone,
                    original_amount,
                    discount_amount,
                    final_amount,
                    status,
                    pickup_at,
                    payment_expires_at
                )
                VALUES (?, ?, 'GENERAL', ?, ?, ?, ?, 40000, 0, 40000,
                        'PENDING_PAYMENT',
                        CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY,
                        CURRENT_TIMESTAMP(6) + INTERVAL 10 MINUTE)
                """,
                orderNumber,
                memberId,
                "주문자",
                "010-1111-2222",
                "수령자",
                "010-3333-4444"
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?",
                Long.class,
                orderNumber
        );
    }

    private void insertOrderItem() {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    product_name,
                    product_type,
                    quantity,
                    base_price,
                    option_amount,
                    total_amount,
                    preparation_days,
                    cancellation_limit_days
                )
                VALUES (?, ?, ?, 'GENERAL', 2, 20000, 0, 40000, 0, 0)
                """,
                orderId,
                productId,
                "결제 완료 테스트 상품 " + suffix
        );
    }

    private void insertReadyPayment() {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTossOrderId("TOSS-PAYMENT-" + suffix);
        payment.setIdempotencyKey("IDEMPOTENCY-PAYMENT-" + suffix);
        payment.setAmount(BigDecimal.valueOf(40_000));

        assertThat(paymentMapper.insertReadyPayment(payment)).isEqualTo(1);
    }
}
