package com.cakeshop.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewSchemaTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reviews_statusDefault_isPublished() {
        Long orderItemId = insertOrderItemChain("default");

        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating, content
                )
                SELECT oi.id, oi.product_id, o.member_id, 5, 5, 5, 5, '기본값 확인'
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE oi.id = ?
                """,
                orderItemId);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE order_item_id = ?", String.class, orderItemId);

        assertThat(status).isEqualTo(ReviewStatus.PUBLISHED.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VISIBLE", "ARCHIVED"})
    void reviews_statusOutsideEnum_isRejectedByCheckConstraint(String status) {
        Long orderItemId = insertOrderItemChain("bad-" + status);

        assertThatThrownBy(() -> insertReview(orderItemId, status, 5, 5, 5, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void reviews_definedStatuses_areAccepted(ReviewStatus status) {
        Long orderItemId = insertOrderItemChain("st-" + status.name());

        assertThatCode(() -> insertReview(orderItemId, status.name(), 5, 5, 5, 5))
                .doesNotThrowAnyException();
    }

    // 제약을 컬럼마다 따로 걸었다. 하나를 빠뜨려도 나머지 셋이 통과하므로 네 컬럼을 모두 돈다.
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "overall_rating, 0",
            "overall_rating, 6",
            "overall_rating, 255",
            "taste_rating,   0",
            "taste_rating,   6",
            "design_rating,  0",
            "design_rating,  6",
            "service_rating, 0",
            "service_rating, 255"
    })
    void reviews_ratingOutOfRange_isRejectedByCheckConstraint(String column, int value) {
        Long orderItemId = insertOrderItemChain("r" + column.charAt(0) + value);

        int overall = "overall_rating".equals(column) ? value : 5;
        int taste = "taste_rating".equals(column) ? value : 5;
        int design = "design_rating".equals(column) ? value : 5;
        int service = "service_rating".equals(column) ? value : 5;

        assertThatThrownBy(
                () -> insertReview(orderItemId, "PUBLISHED", overall, taste, design, service))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 경계 1·5만 확인한다. 사이값은 같은 규칙이라 결과가 달라지지 않는다.
    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void reviews_ratingInRange_isAccepted(int rating) {
        Long orderItemId = insertOrderItemChain("ok" + rating);

        assertThatCode(
                () -> insertReview(orderItemId, "PUBLISHED", rating, rating, rating, rating))
                .doesNotThrowAnyException();
    }

    private void insertReview(
            Long orderItemId, String status,
            int overall, int taste, int design, int service) {
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating, content, status
                )
                SELECT oi.id, oi.product_id, o.member_id, ?, ?, ?, ?, '후기 본문', ?
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE oi.id = ?
                """,
                overall, taste, design, service, status, orderItemId);
    }

    // key 는 members.email · categories.code · orders.order_number 가 UNIQUE 라 테스트마다 다르게 받는다.
    private Long insertOrderItemChain(String key) {
        Long memberId = insertMember(key);
        Long productId = insertProduct(key);

        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, final_amount,
                    status, pickup_at, picked_up_at, created_at, updated_at
                )
                VALUES (?, ?, 'GENERAL', ?, ?, ?, ?, ?, ?, 'PICKED_UP', ?, ?, ?, ?)
                """,
                "ORD-" + key, memberId, "주문자", "010-0000-0000",
                "수령인", "010-0000-0000", new BigDecimal("30000"), new BigDecimal("30000"),
                CREATED_AT, CREATED_AT, CREATED_AT, CREATED_AT);
        Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type,
                    quantity, base_price, total_amount
                )
                VALUES (?, ?, ?, 'GENERAL', 1, ?, ?)
                """,
                orderId, productId, "딸기 생크림 케이크",
                new BigDecimal("30000"), new BigDecimal("30000"));

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertMember(String key) {
        String email = key + "@cakeshop.local";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                email, "encoded-password", "review-test", "010-0000-0000",
                "USER", "ACTIVE", "리뷰 테스트", CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    // GENERAL 이면 preparation_days 가 0 이어야 한다(chk_products_preparation_days_by_type).
    private Long insertProduct(String key) {
        jdbcTemplate.update(
                """
                INSERT INTO categories (code, name, sort_order, created_at, updated_at)
                VALUES (?, ?, 0, ?, ?)
                """,
                "CAT-" + key, "테스트 분류", CREATED_AT, CREATED_AT);
        Long categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, base_price, product_type, status, created_at, updated_at
                )
                VALUES (?, ?, ?, 'GENERAL', 'ACTIVE', ?, ?)
                """,
                categoryId, "딸기 생크림 케이크", new BigDecimal("30000"), CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
