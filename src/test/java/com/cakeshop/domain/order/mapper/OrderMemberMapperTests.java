package com.cakeshop.domain.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class OrderMemberMapperTests {

    private final OrderMemberMapper orderMemberMapper;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;
    private long otherMemberId;
    private long productId;

    @Autowired
    OrderMemberMapperTests(OrderMemberMapper orderMemberMapper, JdbcTemplate jdbcTemplate) {
        this.orderMemberMapper = orderMemberMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember("member");
        otherMemberId = insertMember("other");
        productId = insertProduct();
    }

    @Test
    void findMyPageOrders_filtersMemberAndLimitsEachGroupToFiveNewest() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 10, 0);
        for (int index = 0; index < 6; index++) {
            long orderId = insertOrder(
                    memberId,
                    "PROGRESS-" + index,
                    OrderType.GENERAL,
                    OrderStatus.PENDING_PAYMENT,
                    base.plusDays(index));
            insertOrderItem(orderId, "진행 상품 " + index);
            if (index == 5) {
                insertOrderItem(orderId, "진행 추가 상품");
            }
        }
        for (int index = 0; index < 6; index++) {
            long orderId = insertOrder(
                    memberId,
                    "COMPLETED-" + index,
                    OrderType.GENERAL,
                    OrderStatus.PICKED_UP,
                    base.plusDays(index).plusHours(1));
            insertOrderItem(orderId, "완료 상품 " + index);
        }
        long otherOrderId = insertOrder(
                otherMemberId,
                "OTHER",
                OrderType.CUSTOM,
                OrderStatus.IN_PRODUCTION,
                base.plusDays(20));
        insertOrderItem(otherOrderId, "다른 회원 상품");

        var inProgressOrders = orderMemberMapper.findInProgressOrders(memberId, 5);
        var completedOrders = orderMemberMapper.findCompletedOrders(memberId, 5);

        assertThat(inProgressOrders)
                .extracting(order -> order.orderNumber())
                .containsExactly(
                        orderNumber("PROGRESS-5"),
                        orderNumber("PROGRESS-4"),
                        orderNumber("PROGRESS-3"),
                        orderNumber("PROGRESS-2"),
                        orderNumber("PROGRESS-1"));
        assertThat(inProgressOrders.getFirst().productName()).isEqualTo("진행 상품 5");
        assertThat(inProgressOrders.getFirst().itemCount()).isEqualTo(2);
        assertThat(inProgressOrders).allMatch(order -> order.status() == OrderStatus.PENDING_PAYMENT);

        assertThat(completedOrders)
                .extracting(order -> order.orderNumber())
                .containsExactly(
                        orderNumber("COMPLETED-5"),
                        orderNumber("COMPLETED-4"),
                        orderNumber("COMPLETED-3"),
                        orderNumber("COMPLETED-2"),
                        orderNumber("COMPLETED-1"));
        assertThat(completedOrders).allMatch(order -> order.status() == OrderStatus.PICKED_UP);
    }

    @Test
    void findInProgressOrders_generalAndCustomProgressStatuses_returnsBothOrderTypes() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 10, 0);
        insertOrderWithItem("GENERAL-PENDING", OrderType.GENERAL, OrderStatus.PENDING_PAYMENT, base);
        insertOrderWithItem("GENERAL-READY", OrderType.GENERAL, OrderStatus.READY_FOR_PICKUP, base.plusHours(1));
        insertOrderWithItem("CUSTOM-PENDING", OrderType.CUSTOM, OrderStatus.PENDING_PAYMENT, base.plusHours(2));
        insertOrderWithItem("CUSTOM-REVIEW", OrderType.CUSTOM, OrderStatus.UNDER_REVIEW, base.plusHours(3));
        insertOrderWithItem("CUSTOM-PRODUCTION", OrderType.CUSTOM, OrderStatus.IN_PRODUCTION, base.plusHours(4));
        insertOrderWithItem("CUSTOM-READY", OrderType.CUSTOM, OrderStatus.READY_FOR_PICKUP, base.plusHours(5));
        insertOrderWithItem("COMPLETED", OrderType.GENERAL, OrderStatus.PICKED_UP, base.plusHours(6));

        var orders = orderMemberMapper.findInProgressOrders(memberId, 10);

        assertThat(orders)
                .extracting(order -> order.orderNumber())
                .containsExactly(
                        orderNumber("CUSTOM-READY"),
                        orderNumber("CUSTOM-PRODUCTION"),
                        orderNumber("CUSTOM-REVIEW"),
                        orderNumber("CUSTOM-PENDING"),
                        orderNumber("GENERAL-READY"),
                        orderNumber("GENERAL-PENDING"));
    }

    @Test
    void findCompletedOrders_generalAndCustomOrders_returnsOnlyPickedUp() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 10, 0);
        insertOrderWithItem("GENERAL-PICKED-UP", OrderType.GENERAL, OrderStatus.PICKED_UP, base);
        insertOrderWithItem("CUSTOM-PICKED-UP", OrderType.CUSTOM, OrderStatus.PICKED_UP, base.plusHours(1));
        insertOrderWithItem("CANCELED", OrderType.GENERAL, OrderStatus.CANCELED, base.plusHours(2));
        insertOrderWithItem("REJECTED", OrderType.CUSTOM, OrderStatus.REJECTED, base.plusHours(3));
        insertOrderWithItem("EXPIRED", OrderType.GENERAL, OrderStatus.EXPIRED, base.plusHours(4));

        var orders = orderMemberMapper.findCompletedOrders(memberId, 10);

        assertThat(orders)
                .extracting(order -> order.orderNumber())
                .containsExactly(
                        orderNumber("CUSTOM-PICKED-UP"),
                        orderNumber("GENERAL-PICKED-UP"));
    }

    @Test
    void findOrdersByMemberId_allStatuses_returnsRequestedNewestPage() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 10, 0);
        OrderStatus[] statuses = OrderStatus.values();
        for (int index = 0; index < 12; index++) {
            insertOrderWithItem(
                    "ADMIN-" + index,
                    index % 2 == 0 ? OrderType.GENERAL : OrderType.CUSTOM,
                    statuses[index % statuses.length],
                    index >= 10 ? base.plusHours(10) : base.plusHours(index));
        }
        long otherOrderId = insertOrder(
                otherMemberId,
                "ADMIN-OTHER",
                OrderType.GENERAL,
                OrderStatus.PICKED_UP,
                base.plusDays(2));
        insertOrderItem(otherOrderId, "다른 회원 상품");

        long totalElements = orderMemberMapper.countOrdersByMemberId(memberId);
        var firstPage = orderMemberMapper.findOrdersByMemberId(memberId, 0, 10);
        var secondPage = orderMemberMapper.findOrdersByMemberId(memberId, 10, 10);

        assertThat(totalElements).isEqualTo(12);
        assertThat(firstPage)
                .extracting(order -> order.orderNumber())
                .startsWith(
                        orderNumber("ADMIN-11"),
                        orderNumber("ADMIN-10"));
        assertThat(secondPage)
                .extracting(order -> order.orderNumber())
                .containsExactly(
                        orderNumber("ADMIN-1"),
                        orderNumber("ADMIN-0"));
        assertThat(secondPage)
                .extracting(order -> order.status())
                .containsExactly(statuses[1], statuses[0]);
    }

    private long insertMember(String key) {
        String email = "order-member-" + key + "-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, NULL, '마이페이지 회원', ?, '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email,
                key + suffix
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private long insertProduct() {
        String categoryCode = "ORDER_MEMBER_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, '주문 요약', 999, 1)",
                categoryCode
        );
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );
        String productName = "주문 요약 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, description, base_price, product_type, preparation_days, status
                )
                VALUES (?, ?, '', 35000, 'GENERAL', 0, 'ACTIVE')
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

    private long insertOrder(
            long ownerId,
            String key,
            OrderType orderType,
            OrderStatus status,
            LocalDateTime createdAt
    ) {
        String number = orderNumber(key);
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, discount_amount,
                    final_amount, status, pickup_at, reject_reason, created_at, updated_at, order_type
                )
                VALUES (?, ?, '주문 회원', '010-0000-0000', '픽업 회원', '010-0000-0000',
                        35000, 0, 35000, ?, ?, ?, ?, ?, ?)
                """,
                number,
                ownerId,
                status.name(),
                createdAt.plusDays(7),
                status == OrderStatus.REJECTED ? "테스트 반려" : null,
                createdAt,
                createdAt,
                orderType.name()
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?",
                Long.class,
                number
        );
    }

    private void insertOrderWithItem(
            String key,
            OrderType orderType,
            OrderStatus status,
            LocalDateTime createdAt
    ) {
        long orderId = insertOrder(memberId, key, orderType, status, createdAt);
        insertOrderItem(orderId, key + " 상품");
    }

    private void insertOrderItem(long orderId, String productName) {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type, quantity,
                    base_price, option_amount, total_amount, preparation_days, cancellation_limit_days
                )
                VALUES (?, ?, ?, 'GENERAL', 1, 35000, 0, 35000, 0, 0)
                """,
                orderId,
                productId,
                productName
        );
    }

    private String orderNumber(String key) {
        return key + "-" + suffix;
    }
}
