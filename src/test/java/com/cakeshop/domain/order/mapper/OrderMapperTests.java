package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.product.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderMapper가 실제 DB에 주문 데이터를 저장하고 조회하는지 확인하는 통합 테스트다.
 * JdbcTemplate은 FK 부모 데이터 준비에만 사용하고, 실제 테스트는 OrderMapper로 실행한다.
 * @MybatisTest가 테스트 종료 후 트랜잭션을 롤백하므로 데이터는 DB에 남지 않는다.
 */
@MybatisTest
// application.yml의 local DB 접속 설정을 사용한다.
@ActiveProfiles("local")
// 내장 DB로 바꾸지 않고 개발 PC의 MariaDB를 사용한다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderMapperTests {

    @Autowired
    private OrderMapper orderMapper;

    // 회원·상품처럼 주문 저장 전에 필요한 FK 부모 데이터만 준비한다.
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
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
                    nickname,
                    phone,
                    role,
                    status
                )
                VALUES (?, NULL, ?, ?, 'USER', 'ACTIVE')
                """,
                email,
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
                    cancellation_limit_days,
                    status
                )
                VALUES (?, ?, '', 40000, 'CUSTOM', 3, 2, 'ACTIVE')
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
