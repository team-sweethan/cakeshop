package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.CreateOrderForm;
import com.cakeshop.domain.order.dto.form.OrderItemForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.customer.dto.view.ProductDetailView;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionRow;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.mapper.ProductMapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MybatisTest
@Import(OrderService.class)
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class OrderServiceIntegrationTests {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProductMapper productMapper;

    private String suffix;
    private long memberId;
    private long productId;
    private long productOptionId;

    @Autowired
    OrderServiceIntegrationTests(
            OrderService orderService,
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
        productOptionId = insertProductOption();
        stubProductLookup();
    }

    @Test
    void createGeneralOrderPersistsCalculatedSnapshotsAndReadyPayment() {
        // 실제 Spring Bean이 트랜잭션 프록시로 감싸져 있어야 전체 저장이 한 트랜잭션에 참여한다.
        assertThat(AopUtils.isAopProxy(orderService)).isTrue();
        CreateOrderForm form = createForm();
        LocalDateTime beforeCreation = LocalDateTime.now();

        long orderId = orderService.createGeneralOrder(memberId, form);

        LocalDateTime afterCreation = LocalDateTime.now();
        Order order = orderMapper.findOrderById(orderId).orElseThrow();
        assertThat(order.getMemberId()).isEqualTo(memberId);
        assertThat(order.getOrderType()).isEqualTo(OrderType.GENERAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getOriginalAmount()).isEqualByComparingTo("70000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getFinalAmount()).isEqualByComparingTo("70000");
        assertThat(order.getPaymentExpiresAt())
                .isBetween(
                        beforeCreation.plusMinutes(10).minusSeconds(1),
                        afterCreation.plusMinutes(10).plusSeconds(1)
                );

        OrderItem savedItem = orderMapper.findOrderItemsByOrderId(orderId)
                .getFirst();
        assertThat(savedItem.getProductId()).isEqualTo(productId);
        assertThat(savedItem.getProductName())
                .isEqualTo("일반 주문 서비스 상품 " + suffix);
        assertThat(savedItem.getBasePrice()).isEqualByComparingTo("30000");
        assertThat(savedItem.getOptionAmount()).isEqualByComparingTo("5000");
        assertThat(savedItem.getQuantity()).isEqualTo(2);
        assertThat(savedItem.getTotalAmount()).isEqualByComparingTo("70000");

        OrderItemOption savedOption =
                orderMapper.findOrderItemOptionsByOrderId(orderId).getFirst();
        assertThat(savedOption.getProductOptionId()).isEqualTo(productOptionId);
        assertThat(savedOption.getOptionGroupName()).isEqualTo("크기");
        assertThat(savedOption.getOptionName()).isEqualTo("2호");
        assertThat(savedOption.getAdditionalPrice()).isEqualByComparingTo("5000");

        Payment payment = paymentMapper.findPaymentsByOrderId(orderId).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getAmount()).isEqualByComparingTo("70000");
        assertThat(payment.getTossOrderId()).isEqualTo(order.getOrderNumber());
        assertThat(payment.getIdempotencyKey()).startsWith("PAY-");
    }

    private void stubProductLookup() {
        ProductDetailView product = new ProductDetailView();
        product.setId(productId);
        product.setName("일반 주문 서비스 상품 " + suffix);
        product.setBasePrice(BigDecimal.valueOf(30_000));
        product.setProductType(ProductType.GENERAL);
        product.setPreparationDays(0);
        when(productMapper.findPublicDetailById(productId)).thenReturn(product);
        when(productMapper.findPublicOptionRowsByProductId(productId))
                .thenReturn(List.of(new ProductOptionRow(
                        1L,
                        "크기",
                        true,
                        "SINGLE",
                        productOptionId,
                        "2호",
                        BigDecimal.valueOf(5_000)
                )));
    }

    private CreateOrderForm createForm() {
        OrderItemForm item = new OrderItemForm();
        item.setProductId(productId);
        item.setQuantity(2);
        item.setOptionIds(List.of(productOptionId));

        CreateOrderForm form = new CreateOrderForm();
        form.setOrdererName("주문자");
        form.setOrdererPhone("010-1111-2222");
        form.setPickupName("수령자");
        form.setPickupPhone("010-3333-4444");
        form.setPickupAt(LocalDateTime.now().plusDays(3));
        form.setRequestMessage("초는 빼주세요.");
        form.setItems(List.of(item));
        return form;
    }

    private long insertMember() {
        String email = "order-service-" + suffix + "@example.com";
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
                "주문 서비스 테스트 회원",
                "주문서비스테스트",
                "010-0000-0000"
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private long insertProduct() {
        String categoryCode = "ORDER_SERVICE_" + suffix;
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
                "주문 서비스 테스트"
        );
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        String productName = "일반 주문 서비스 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    description,
                    base_price,
                    product_type,
                    preparation_days,
                    status
                )
                VALUES (?, ?, '', 30000, 'GENERAL', 0, 'ACTIVE')
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

    private long insertProductOption() {
        jdbcTemplate.update(
                """
                INSERT INTO product_option_groups (
                    product_id,
                    name,
                    required,
                    selection_type,
                    sort_order
                )
                VALUES (?, '크기', 1, 'SINGLE', 1)
                """,
                productId
        );
        long groupId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_groups
                WHERE product_id = ?
                  AND name = '크기'
                """,
                Long.class,
                productId
        );

        jdbcTemplate.update(
                """
                INSERT INTO product_options (
                    option_group_id,
                    name,
                    additional_price,
                    status,
                    sort_order
                )
                VALUES (?, '2호', 5000, 'ACTIVE', 1)
                """,
                groupId
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_options
                WHERE option_group_id = ?
                  AND name = '2호'
                """,
                Long.class,
                groupId
        );
    }
}
