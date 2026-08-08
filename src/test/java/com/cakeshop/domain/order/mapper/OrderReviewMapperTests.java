package com.cakeshop.domain.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
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
 * <p><b>이 테스트는 {@code reviews} 에 한 행도 넣지 않는다.</b> 제외할 식별자를 인자로 받는 설계라
 * 주문 쪽 SQL 이 후기 테이블을 알 필요가 없다 — 그 사실 자체가 계약이 지키려는 것이고, 여기에
 * {@code reviews} INSERT 가 생기는 날이 경계가 무너진 날이다.</p>
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

    /**
     * <b>이 계약이 존재하는 이유를 그대로 고정한다.</b>
     *
     * <p>픽업 완료 3건 중 <b>최신 2건을 이미 작성</b>했고 페이지 크기가 2다. 계약이 페이지를 먼저
     * 자르고 호출한 쪽이 뒤에서 걸렀다면 첫 페이지는 <b>통째로 비고</b> 남은 1건은 2페이지로
     * 밀린다. 목록이 비는 것은 정상 상태라 오류로도 드러나지 않아 아무도 모른다.
     */
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

    /** 건수도 같은 조건을 써야 한다. 갈리면 화면의 총 페이지 수가 실제와 다르다. */
    @Test
    void countWritableOrderItems_appliesSameExclusion() {
        insertPickedUpOrderItem("케이크1", PICKED_UP_AT);
        long written = insertPickedUpOrderItem("케이크2", PICKED_UP_AT.plusDays(1));

        assertThat(orderReviewMapper.countWritableOrderItems(memberId, List.of(written)))
                .isEqualTo(1);
    }

    /**
     * 후기를 한 건도 안 쓴 회원은 제외 목록이 비어서 들어온다. {@code NOT IN ()} 은 문법 오류라
     * 조건 자체를 빼지 않으면 <b>첫 방문에서 바로</b> 500 이다.
     */
    @Test
    void findWritableOrderItems_emptyExclusion_doesNotBreakSql() {
        long item = insertPickedUpOrderItem("첫 주문 케이크", PICKED_UP_AT);

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20))
                .extracting(OrderReviewItemView::orderItemId)
                .containsExactly(item);
        assertThat(orderReviewMapper.countWritableOrderItems(memberId, List.of())).isEqualTo(1);
    }

    /** 픽업 전 주문은 후기 대상이 아니다. 상태 조건이 빠지면 결제만 한 주문에 후기가 열린다. */
    @Test
    void findWritableOrderItems_notPickedUpOrder_isExcluded() {
        insertOrderItem(insertOrder(memberId, "PENDING_PAYMENT", null), "결제대기 케이크");

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20)).isEmpty();
    }

    /** 소유 조건이 빠지면 남의 주문에 후기를 쓸 수 있게 된다. */
    @Test
    void findWritableOrderItems_otherMembersOrder_isExcluded() {
        long otherMemberId = insertMember();
        insertOrderItem(
                insertOrder(otherMemberId, "PICKED_UP", PICKED_UP_AT), "남의 케이크");

        assertThat(orderReviewMapper.findWritableOrderItems(memberId, List.of(), 0, 20)).isEmpty();
    }

    /**
     * <b>같은 주문의 상품들은 {@code picked_up_at} 이 전부 같다.</b> 정렬 키가 그것뿐이면 순서가
     * 흔들려 같은 항목이 두 페이지에 나오거나 어느 페이지에도 안 나온다.
     */
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
        assertThat(page1)
                .as("두 페이지를 합치면 세 건이 빠짐없이 나와야 한다")
                .containsAll(List.of(third, second));
        assertThat(page2).containsExactly(first);
    }

    /** 검증에 필요한 값이 실제로 채워져 오는지. product_id 는 요청값 대신 여기서 파생시킨다 (R4). */
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

    /**
     * <b>픽업 전이어도 비우지 않고 돌려준다.</b> 픽업 전은 400 이고 없음·남의 것은 404 라 응답이
     * 다르다. 여기서 함께 걸러 내면 호출한 쪽이 둘을 구분할 방법이 없어진다.
     */
    @Test
    void findReviewTarget_notPickedUp_isReturnedWithFalseFlag() {
        long orderItemId =
                insertOrderItem(insertOrder(memberId, "PENDING_PAYMENT", null), "결제대기 케이크");

        OrderReviewTargetView target = orderReviewMapper.findReviewTarget(orderItemId, memberId);

        assertThat(target).as("픽업 전이라고 비우면 400 과 404 를 못 가른다").isNotNull();
        assertThat(target.pickedUp()).isFalse();
        assertThat(target.pickedUpAt()).isNull();
    }

    /** 남의 주문 상품은 존재 자체를 알려 주지 않는다 — 호출한 쪽에서 404 가 된다 (DOMAIN 2.5). */
    @Test
    void findReviewTarget_otherMembersItem_isNull() {
        long otherMemberId = insertMember();
        long orderItemId =
                insertOrderItem(insertOrder(otherMemberId, "PICKED_UP", PICKED_UP_AT), "남의 케이크");

        assertThat(orderReviewMapper.findReviewTarget(orderItemId, memberId)).isNull();
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
                    base_price, option_amount, total_amount, preparation_days,
                    cancellation_limit_days
                ) VALUES (?, ?, ?, 'GENERAL', 1, 20000, 0, 20000, 0, 0)
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
