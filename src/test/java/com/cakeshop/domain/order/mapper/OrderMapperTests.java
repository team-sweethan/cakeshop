package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderMapper가 실제 DB에 주문 데이터를 저장하고 조회하는지 확인하는 통합 테스트다.
 * JdbcTemplate은 FK 부모 데이터 준비에만 사용하고, 실제 테스트는 OrderMapper로 실행한다.
 * @MybatisTest가 테스트 종료 후 트랜잭션을 롤백하므로 데이터는 DB에 남지 않는다.
 */
@MybatisTest
// 내장 DB로 바꾸지 않고 Testcontainers의 MariaDB를 사용한다.
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 각 테스트가 끝나면 JdbcTemplate과 Mapper가 저장한 데이터를 함께 롤백한다.
@Transactional
class OrderMapperTests {

    private final OrderMapper orderMapper;

    // 회원·상품처럼 주문 저장 전에 필요한 FK 부모 데이터만 준비한다.
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OrderMapperTests(OrderMapper orderMapper, JdbcTemplate jdbcTemplate) {
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // 반복 실행해도 이메일·주문번호 등이 중복되지 않게 붙이는 값이다.
    private String suffix;
    private long memberId;
    private long productId;
    private long productOptionId;

    // 각 테스트 전에 FK로 필요한 회원·상품·상품 옵션을 먼저 만든다.
    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        insertMember();
        insertProductAndOption();
    }

    // 주문을 INSERT하고 생성된 ID로 다시 SELECT했을 때 같은 값인지 확인한다.
    @Test
    void insertOrderAndFindOrderById() {
        // Given: 저장할 주문 객체 준비
        Order order = newOrder();

        // When: OrderMapper.insertOrder → XML의 insertOrder SQL 실행
        assertThat(orderMapper.insertOrder(order)).isEqualTo(1);
        // useGeneratedKeys로 DB가 만든 orders.id가 객체에 들어와야 한다.
        assertThat(order.getId()).isNotNull();

        // When: OrderMapper.findOrderById → XML의 findOrderById SQL 실행
        Order savedOrder = orderMapper.findOrderById(order.getId())
                .orElseThrow();

        // Then: DB 자동 생성 시각을 제외한 모든 필드가 저장 전 객체와 같아야 한다.
        assertThat(savedOrder)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(order);
        // orders.order_type에 Java enum 이름 CUSTOM이 그대로 저장·조회되어야 한다.
        assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.CUSTOM);
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
    }

    // 주문 ID와 회원 ID가 모두 일치할 때만 소유 주문으로 확인되는지 검증한다.
    @Test
    void existsByIdAndMemberIdChecksOrderOwner() {
        Order order = newOrder();
        orderMapper.insertOrder(order);

        // 같은 주문 ID와 실제 주문 회원 ID이므로 true다.
        assertThat(orderMapper.existsByIdAndMemberId(
                order.getId(),
                memberId
        )).isTrue();

        // 주문 ID는 같지만 다른 회원 ID이므로 false다.
        assertThat(orderMapper.existsByIdAndMemberId(
                order.getId(),
                memberId + 1
        )).isFalse();
    }

    @Test
    void approveIfUnderReview_recordsStatusTimeAndProcessorConditionally() {
        Order order = newOrder();
        orderMapper.insertOrder(order);
        LocalDateTime underReviewAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime readyAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);

        assertThat(orderMapper.approveIfUnderReview(
                order.getId(),
                memberId,
                readyAt
        )).isZero();

        assertThat(orderMapper.markUnderReviewAfterPaymentIfPending(
                order.getId(),
                underReviewAt
        )).isEqualTo(1);
        Order underReview = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(underReview.getStatus()).isEqualTo(OrderStatus.UNDER_REVIEW);
        assertThat(underReview.getUnderReviewAt()).isEqualTo(underReviewAt);

        assertThat(orderMapper.approveIfUnderReview(
                order.getId(),
                memberId,
                readyAt
        )).isEqualTo(1);
        Order approved = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(approved.getApprovedBy()).isEqualTo(memberId);
        assertThat(approved.getReadyAt()).isEqualTo(readyAt);

        assertThat(orderMapper.approveIfUnderReview(
                order.getId(),
                memberId,
                readyAt.plusMinutes(1)
        )).isZero();
        assertThat(orderMapper.cancelIfCurrent(
                order.getId(),
                OrderStatus.READY_FOR_PICKUP,
                "CUSTOMER",
                "승인 후 취소 시도",
                readyAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void markPickedUpIfReady_recordsStatusTimeAndProcessorConditionally() {
        Order order = newOrder();
        order.setOrderType(OrderType.GENERAL);
        orderMapper.insertOrder(order);
        LocalDateTime readyAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime pickedUpAt =
                LocalDateTime.of(2026, 8, 10, 14, 5);

        assertThat(orderMapper.markReadyForPickupAfterPaymentIfPending(
                order.getId(),
                readyAt
        )).isEqualTo(1);
        assertThat(orderMapper.markPickedUpIfReady(
                order.getId(),
                memberId,
                pickedUpAt
        )).isEqualTo(1);

        Order pickedUp = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(pickedUp.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(pickedUp.getReadyAt()).isEqualTo(readyAt);
        assertThat(pickedUp.getPickedUpBy()).isEqualTo(memberId);
        assertThat(pickedUp.getPickedUpAt()).isEqualTo(pickedUpAt);
        assertThat(orderMapper.markPickedUpIfReady(
                order.getId(),
                memberId,
                pickedUpAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void rejectIfUnderReview_recordsReasonTimeAndProcessorConditionally() {
        Order order = newOrder();
        orderMapper.insertOrder(order);
        LocalDateTime underReviewAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime rejectedAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);

        orderMapper.markUnderReviewAfterPaymentIfPending(
                order.getId(),
                underReviewAt
        );
        assertThat(orderMapper.rejectIfUnderReview(
                order.getId(),
                memberId,
                rejectedAt,
                "제작 일정이 부족합니다."
        )).isEqualTo(1);

        Order rejected = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectedBy()).isEqualTo(memberId);
        assertThat(rejected.getRejectedAt()).isEqualTo(rejectedAt);
        assertThat(rejected.getRejectReason()).isEqualTo("제작 일정이 부족합니다.");
        assertThat(orderMapper.rejectIfUnderReview(
                order.getId(),
                memberId,
                rejectedAt.plusMinutes(1),
                "다시 반려"
        )).isZero();
    }

    @Test
    void expireIfPendingPayment_recordsStatusAndTimeConditionally() {
        Order order = newOrder();
        orderMapper.insertOrder(order);
        LocalDateTime expiredAt =
                LocalDateTime.of(2026, 8, 1, 12, 11);

        assertThat(orderMapper.expireIfPendingPayment(
                order.getId(),
                expiredAt
        )).isEqualTo(1);

        Order expired = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(expired.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(orderMapper.expireIfPendingPayment(
                order.getId(),
                expiredAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void cancelIfCurrent_recordsActorReasonAndTimeOnlyFromCancelableStatus() {
        Order order = newOrder();
        orderMapper.insertOrder(order);
        LocalDateTime underReviewAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime canceledAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);

        assertThat(orderMapper.cancelIfCurrent(
                order.getId(),
                OrderStatus.PENDING_PAYMENT,
                "CUSTOMER",
                "단순 변심",
                canceledAt
        )).isZero();

        orderMapper.markUnderReviewAfterPaymentIfPending(
                order.getId(),
                underReviewAt
        );
        assertThat(orderMapper.cancelIfCurrent(
                order.getId(),
                OrderStatus.UNDER_REVIEW,
                "CUSTOMER",
                "단순 변심",
                canceledAt
        )).isEqualTo(1);

        Order canceled = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getCanceledBy()).isEqualTo("CUSTOMER");
        assertThat(canceled.getCancelReason()).isEqualTo("단순 변심");
        assertThat(canceled.getCanceledAt()).isEqualTo(canceledAt);
    }

    @Test
    void cancelIfCurrent_allowsGeneralOrderOnlyAfterPayment() {
        Order order = newOrder();
        order.setOrderType(OrderType.GENERAL);
        orderMapper.insertOrder(order);
        LocalDateTime readyAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime canceledAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);

        orderMapper.markReadyForPickupAfterPaymentIfPending(
                order.getId(),
                readyAt
        );
        assertThat(orderMapper.cancelIfCurrent(
                order.getId(),
                OrderStatus.READY_FOR_PICKUP,
                "CUSTOMER",
                "픽업 전 취소",
                canceledAt
        )).isEqualTo(1);

        Order canceled = orderMapper.findOrderById(order.getId())
                .orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getCanceledBy()).isEqualTo("CUSTOMER");
        assertThat(canceled.getCancelReason()).isEqualTo("픽업 전 취소");
        assertThat(canceled.getCanceledAt()).isEqualTo(canceledAt);
    }

    @Test
    void invalidOrderStatusCannotBeStored() {
        Order order = newOrder();
        orderMapper.insertOrder(order);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE orders SET status = 'INVALID_STATUS' WHERE id = ?",
                order.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // 주문 하위 데이터 3종을 INSERT하고 orderId로 다시 SELECT하는지 확인한다.
    @Test
    void insertAndFindOrderItemsOptionsAndImages() {
        // FK 부모인 주문부터 저장한다.
        Order order = newOrder();
        orderMapper.insertOrder(order);

        // OrderMapper.insertOrderItem 호출
        OrderItem orderItem = newOrderItem(order.getId());
        assertThat(orderMapper.insertOrderItem(orderItem)).isEqualTo(1);
        assertThat(orderItem.getId()).isNotNull();

        // OrderMapper.insertOrderItemOption 호출
        OrderItemOption option = newOrderItemOption(orderItem.getId());
        assertThat(orderMapper.insertOrderItemOption(option)).isEqualTo(1);
        assertThat(option.getId()).isNotNull();

        // 이미지 정렬 확인을 위해 sortOrder=2 이미지를 먼저 저장할 준비를 한다.
        OrderItemImage secondImage = newOrderItemImage(
                orderItem.getId(),
                "/test/" + suffix + "/second.jpg",
                2
        );
        OrderItemImage firstImage = newOrderItemImage(
                orderItem.getId(),
                "/test/" + suffix + "/first.jpg",
                1
        );

        // OrderMapper.insertOrderItemImage를 두 번 호출한다.
        assertThat(orderMapper.insertOrderItemImage(secondImage)).isEqualTo(1);
        assertThat(orderMapper.insertOrderItemImage(firstImage)).isEqualTo(1);
        assertThat(secondImage.getId()).isNotNull();
        assertThat(firstImage.getId()).isNotNull();

        // OrderMapper.findOrderItemsByOrderId 결과 검증
        assertThat(orderMapper.findOrderItemsByOrderId(order.getId()))
                .singleElement()
                .usingRecursiveComparison()
                .isEqualTo(orderItem);

        // OrderMapper.findOrderItemOptionsByOrderId 결과 검증
        assertThat(orderMapper.findOrderItemOptionsByOrderId(order.getId()))
                .singleElement()
                .usingRecursiveComparison()
                .isEqualTo(option);

        // OrderMapper.findOrderItemImagesByOrderId 결과 조회
        List<OrderItemImage> images =
                orderMapper.findOrderItemImagesByOrderId(order.getId());

        // 저장 순서와 달라도 sort_order 기준으로 1번 → 2번 순서여야 한다.
        assertThat(images)
                .extracting(OrderItemImage::getImageUrl)
                .containsExactly(
                        firstImage.getImageUrl(),
                        secondImage.getImageUrl()
                );
    }

    // 테스트용 Order 객체를 만든다.
    private Order newOrder() {
        Order order = new Order();
        order.setOrderNumber("ORDER-MAPPER-" + suffix);
        order.setMemberId(memberId);
        order.setOrderType(OrderType.CUSTOM);
        order.setOrdererName("주문자");
        order.setOrdererPhone("010-1111-2222");
        order.setPickupName("수령자");
        order.setPickupPhone("010-3333-4444");
        order.setOriginalAmount(BigDecimal.valueOf(45_000));
        order.setDiscountAmount(BigDecimal.valueOf(5_000));
        order.setFinalAmount(BigDecimal.valueOf(40_000));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPickupAt(LocalDateTime.of(2026, 8, 10, 14, 0));
        order.setCancellationBlockedAt(
                LocalDateTime.of(2026, 8, 8, 20, 0)
        );
        order.setPaymentExpiresAt(
                LocalDateTime.of(2026, 8, 1, 12, 10)
        );
        order.setRequestMessage("문구는 생일 축하합니다.");
        return order;
    }

    // 테스트용 OrderItem 객체를 만든다.
    private OrderItem newOrderItem(long orderId) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(productId);
        orderItem.setProductName("주문 제작 케이크");
        orderItem.setProductType(ProductType.CUSTOM);
        orderItem.setQuantity(1);
        orderItem.setBasePrice(BigDecimal.valueOf(40_000));
        orderItem.setOptionAmount(BigDecimal.valueOf(5_000));
        orderItem.setTotalAmount(BigDecimal.valueOf(45_000));
        orderItem.setRequirements("분홍색 크림");
        orderItem.setPreparationDays(3);
        orderItem.setCancellationLimitDays(2);
        return orderItem;
    }

    // 테스트용 OrderItemOption 객체를 만든다.
    private OrderItemOption newOrderItemOption(long orderItemId) {
        OrderItemOption option = new OrderItemOption();
        option.setOrderItemId(orderItemId);
        option.setProductOptionId(productOptionId);
        option.setOptionGroupName("크기");
        option.setOptionName("2호");
        option.setAdditionalPrice(BigDecimal.valueOf(5_000));
        return option;
    }

    // 테스트용 OrderItemImage 객체를 만든다.
    private OrderItemImage newOrderItemImage(long orderItemId, String imageUrl, int sortOrder) {
        OrderItemImage image = new OrderItemImage();
        image.setOrderItemId(orderItemId);
        image.setImageUrl(imageUrl);
        image.setSortOrder(sortOrder);
        return image;
    }

    // orders.member_id FK를 만족시킬 회원을 JdbcTemplate으로 준비한다.
    private void insertMember() {
        String email = "order-mapper-" + suffix + "@example.com";

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
                "주문 테스트 회원",
                "주문테스트",
                "010-0000-0000"
        );

        memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    // 상품 관련 FK를 만족시킬 카테고리·상품·옵션을 JdbcTemplate으로 준비한다.
    private void insertProductAndOption() {
        String categoryCode = "ORDER_MAPPER_" + suffix;

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
                "주문 Mapper 테스트"
        );

        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        String productName = "주문 Mapper 상품 " + suffix;
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
                VALUES (?, ?, '', 40000, 'CUSTOM', 3, 'ACTIVE')
                """,
                categoryId,
                productName
        );

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                productName
        );

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

        long optionGroupId = jdbcTemplate.queryForObject(
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
                optionGroupId
        );

        productOptionId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_options
                WHERE option_group_id = ?
                  AND name = '2호'
                """,
                Long.class,
                optionGroupId
        );
    }
}
