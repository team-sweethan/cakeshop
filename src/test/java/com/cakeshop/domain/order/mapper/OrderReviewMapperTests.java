package com.cakeshop.domain.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성용 주문 상품 조회 SQL 검증
 * 설명 : 제외 목록을 SQL 안에서 적용하는 것과 소유 회원 판정을 MariaDB Testcontainers 로 고정한다.
 * ******************************
 *
 * <p>이 테스트는 {@code reviews} 에 한 행도 넣지 않는다. 제외할 식별자를 인자로 받는 설계라
 * 주문 쪽 SQL 이 후기 테이블을 알 필요가 없고, 여기에 {@code reviews} INSERT 가 생기는 날이
 * 경계가 무너진 날이다.</p>
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderReviewMapperTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private OrderReviewMapper orderReviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long productId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
    }

    @Test
    void findWritableOrderItems_excludesInsideSql_soFirstPageIsNotEmpty() {
        long oldest = insertPickedUpOrderItem("오래된 케이크", PICKED_UP_AT);
        long middle = insertPickedUpOrderItem("중간 케이크", PICKED_UP_AT.plusDays(1));
        long newest = insertPickedUpOrderItem("최신 케이크", PICKED_UP_AT.plusDays(2));

        List<OrderReviewItemView> firstPage =
                orderReviewMapper.findWritableOrderItems(
                        memberId, List.of(newest, middle), 0, 2);

        assertThat(firstPage)
                .as("최신 2건을 이미 썼어도 첫 페이지는 남은 1건을 보여야 한다")
                .extracting(OrderReviewItemView::orderItemId)
                .containsExactly(oldest);
    }

    @Test
    void countWritableOrderItems_appliesSameExclusion() {
        insertPickedUpOrderItem("케이크1", PICKED_UP_AT);
        long written = insertPickedUpOrderItem("케이크2", PICKED_UP_AT.plusDays(1));

        assertThat(orderReviewMapper.countWritableOrderItems(memberId, List.of(written)))
                .as("건수가 목록과 갈리면 화면의 총 페이지 수가 실제와 다르다")
                .isEqualTo(1);
    }

    @Test
    void findWritableOrderItems_emptyExclusion_doesNotBreakSql() {
        long item = insertPickedUpOrderItem("첫 주문 케이크", PICKED_UP_AT);

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20))
                .extracting(OrderReviewItemView::orderItemId)
                .containsExactly(item);
        assertThat(orderReviewMapper.countWritableOrderItems(memberId, List.of())).isEqualTo(1);
    }

    @Test
    void findWritableOrderItems_notPickedUpOrder_isExcluded() {
        insertOrderItem(insertOrder(memberId, "PENDING_PAYMENT", null), "결제대기 케이크");

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20)).isEmpty();
    }

    @Test
    void findWritableOrderItems_otherMembersOrder_isExcluded() {
        long otherMemberId = insertMember();
        insertOrderItem(
                insertOrder(otherMemberId, "PICKED_UP", PICKED_UP_AT), "남의 케이크");

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20)).isEmpty();
    }

    /** 한 주문의 상품들은 {@code picked_up_at} 이 전부 같아 정렬 키가 그것뿐이면 순서가 흔들린다. */
    @Test
    void findWritableOrderItems_samePickedUpAt_pagesDoNotOverlapOrSkip() {
        long orderId = insertOrder(memberId, "PICKED_UP", PICKED_UP_AT);
        long first = insertOrderItem(orderId, "케이크A");
        long second = insertOrderItem(orderId, "케이크B");
        long third = insertOrderItem(orderId, "케이크C");

        List<Long> page1 = idsOf(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 2));
        List<Long> page2 = idsOf(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 2, 2));

        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
        assertThat(page1).doesNotContainAnyElementsOf(page2);
        assertThat(page1).containsAll(List.of(third, second));
        assertThat(page2).containsExactly(first);
    }

    @Test
    void findReviewTarget_pickedUpItem_carriesProductIdAndPickedUpFlag() {
        long orderItemId = insertPickedUpOrderItem("픽업 케이크", PICKED_UP_AT);

        OrderReviewTargetView target = orderReviewMapper.findReviewTarget(orderItemId, memberId);

        assertThat(target).isNotNull();
        assertThat(target.orderItemId()).isEqualTo(orderItemId);
        assertThat(target.productId()).isEqualTo(productId);
        assertThat(target.productName()).isEqualTo("픽업 케이크");
        assertThat(target.pickedUp()).isTrue();
        assertThat(target.pickedUpAt()).isEqualTo(PICKED_UP_AT);
    }

    @Test
    void findReviewTarget_notPickedUp_isReturnedWithFalseFlag() {
        long orderItemId =
                insertOrderItem(insertOrder(memberId, "PENDING_PAYMENT", null), "결제대기 케이크");

        OrderReviewTargetView target = orderReviewMapper.findReviewTarget(orderItemId, memberId);

        assertThat(target).as("픽업 전이라고 비우면 400 과 404 를 못 가른다").isNotNull();
        assertThat(target.pickedUp()).isFalse();
        assertThat(target.pickedUpAt()).isNull();
    }

    @Test
    void findReviewTarget_otherMembersItem_isNull() {
        long otherMemberId = insertMember();
        long orderItemId =
                insertOrderItem(insertOrder(otherMemberId, "PICKED_UP", PICKED_UP_AT), "남의 케이크");

        assertThat(orderReviewMapper.findReviewTarget(orderItemId, memberId)).isNull();
    }

    @Test
    void findSnapshotsByOrderItemIds_carriesProductNameSnapshotAndOrderNumber() {
        long orderId = insertOrder(memberId, "PICKED_UP", PICKED_UP_AT);
        long orderItemId = insertOrderItem(orderId, "주문 시점 케이크");
        jdbcTemplate.update("UPDATE products SET name = ? WHERE id = ?", "이름 바뀐 케이크", productId);
        String orderNumber = jdbcTemplate.queryForObject(
                "SELECT order_number FROM orders WHERE id = ?", String.class, orderId);

        List<OrderReviewSnapshotView> snapshots =
                orderReviewMapper.findSnapshotsByOrderItemIds(List.of(orderItemId, Long.MAX_VALUE));

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.orderItemId()).isEqualTo(orderItemId);
            assertThat(snapshot.productName()).isEqualTo("주문 시점 케이크");
            assertThat(snapshot.orderNumber()).isEqualTo(orderNumber);
        });
    }

    @Test
    void findSnapshotsByOrderItemIds_otherMembersItem_isStillReturnedForAdminUse() {
        long otherMemberId = insertMember();
        long orderItemId =
                insertOrderItem(insertOrder(otherMemberId, "PICKED_UP", PICKED_UP_AT), "남의 케이크");

        assertThat(orderReviewMapper.findSnapshotsByOrderItemIds(List.of(orderItemId)))
                .as("소유권 판정은 후기를 고르는 자리에 있고 이 조회는 관리자 목록도 쓴다")
                .extracting(OrderReviewSnapshotView::productName)
                .containsExactly("남의 케이크");
    }

    @Test
    void findOrderItemIdsByProductName_matchesPartOfTheSnapshotName() {
        long strawberry = insertPickedUpOrderItem("딸기 생크림 케이크", PICKED_UP_AT);
        long chocolate = insertPickedUpOrderItem("초코 케이크", PICKED_UP_AT);

        List<Long> ids = orderReviewMapper.findOrderItemIdsByProductName("딸기");

        assertThat(ids).contains(strawberry).doesNotContain(chocolate);
    }

    @Test
    void findOrderItemIdsByProductName_escapedWildcard_matchesTheLiteralCharacter() {
        long literal = insertPickedUpOrderItem("50% 할인 케이크", PICKED_UP_AT);
        long other = insertPickedUpOrderItem("정가 케이크", PICKED_UP_AT);

        List<Long> ids = orderReviewMapper.findOrderItemIdsByProductName("!% 할인");

        assertThat(ids).contains(literal).doesNotContain(other);
    }

    @Test
    void findOrderItemIdsByProductName_looksAtTheSnapshotNotTheProductTable() {
        long orderItemId = insertPickedUpOrderItem("주문 당시 이름", PICKED_UP_AT);
        jdbcTemplate.update("UPDATE products SET name = ? WHERE id = ?", "바뀐 이름", productId);

        assertThat(orderReviewMapper.findOrderItemIdsByProductName("주문 당시"))
                .as("관리자는 화면에 보이는 스냅샷 이름 그대로 검색할 수 있어야 한다")
                .contains(orderItemId);
        assertThat(orderReviewMapper.findOrderItemIdsByProductName("바뀐 이름"))
                .doesNotContain(orderItemId);
    }

    private List<Long> idsOf(List<OrderReviewItemView> items) {
        return items.stream().map(OrderReviewItemView::orderItemId).toList();
    }

    private long insertPickedUpOrderItem(String productName, LocalDateTime pickedUpAt) {
        return insertOrderItem(insertOrder(memberId, "PICKED_UP", pickedUpAt), productName);
    }

    private long insertMember() {
        String email = "order-review-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '주문자', ?, '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email,
                "닉" + System.nanoTime());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct() {
        String categoryCode = "ORDER_REVIEW_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "후기 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = "후기 대상 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, description, base_price, product_type,
                    preparation_days, stock_quantity, status
                ) VALUES (?, ?, '', 20000, 'GENERAL', 0, 5, 'ACTIVE')
                """,
                categoryId,
                productName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?", Long.class, productName);
    }

    private long insertOrder(long ownerId, String status, LocalDateTime pickedUpAt) {
        String orderNumber = "ORDER-REVIEW-" + System.nanoTime();
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, discount_amount,
                    final_amount, status, pickup_at, picked_up_at
                ) VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222', '수령자',
                          '010-3333-4444', 20000, 0, 20000, ?, ?, ?)
                """,
                orderNumber,
                ownerId,
                status,
                PICKED_UP_AT.minusDays(1),
                pickedUpAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?", Long.class, orderNumber);
    }

    private long insertOrderItem(long orderId, String productName) {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type, quantity,
                    base_price, option_amount, total_amount, preparation_days
                ) VALUES (?, ?, ?, 'GENERAL', 1, 20000, 0, 20000, 0)
                """,
                orderId,
                productId,
                productName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ? AND product_name = ?",
                Long.class,
                orderId,
                productName);
    }
}
