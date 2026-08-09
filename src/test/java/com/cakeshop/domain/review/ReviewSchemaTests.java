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

/**
 * 조각 0(#108)에서 추가한 migration 을 검증한다.
 *
 * <p>상태 어휘와 평점 범위 둘 다 docs/review/DOMAIN.md 의 결정을 DB 에 못 박은 것이므로, 규칙이
 * 코드에서만 지켜지고 DB 에서는 뚫리는 상황을 여기서 잡는다. 선례는 CommunitySchemaTests 다.
 *
 * <p>여기서 지키는 것은 <b>화면 검증이 막지 못하는 경로</b>다. 상태 어휘든 평점 범위든 폼을
 * 거치지 않은 직접 호출이면 그대로 통과하고, 그렇게 들어간 값은 화면에 정상으로 보인다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewSchemaTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 기본값이 {@code 'PUBLISHED'}로 바뀌었는지 확인한다.
     *
     * <p>V0 의 기본값 {@code 'VISIBLE'}은 코드베이스 어디에도 없는 어휘였다. 상태를 넣지 않는
     * INSERT 가 하나라도 남으면 그 행은 어느 조회에도 걸리지 않는다 — 노출 판단이
     * {@code status = 'PUBLISHED'} 하나이기 때문이다. <b>저장은 성공하고 화면에서만 사라진다.</b>
     */
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

    /**
     * 허용 집합 밖의 상태가 실제로 거절되는지 확인한다.
     *
     * <p>기본값만 바꾸고 CHECK 를 걸지 않으면 값을 명시하는 INSERT 는 여전히 통과한다. 기본값
     * 변경과 제약은 한 쌍이라 둘 중 하나만으로는 어휘가 갈리는 것을 막지 못한다.
     * {@code 'VISIBLE'} 은 조각 0 전까지 스키마 기본값이던 옛 어휘라 특히 되돌아오기 쉽다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"VISIBLE", "ARCHIVED"})
    void reviews_statusOutsideEnum_isRejectedByCheckConstraint(String status) {
        Long orderItemId = insertOrderItemChain("bad-" + status);

        assertThatThrownBy(() -> insertReview(orderItemId, status, 5, 5, 5, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * enum 이 정의한 세 값이 전부 저장되는지 확인한다.
     *
     * <p>{@code uk_reviews_order_item} 이 주문 상품당 1건을 강제하므로 상태마다 주문 상품이
     * 달라야 한다. 이 UNIQUE 가 조각 1 의 전제이자 R10(삭제하면 재작성 불가)의 원인이다.
     */
    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void reviews_definedStatuses_areAccepted(ReviewStatus status) {
        Long orderItemId = insertOrderItemChain("st-" + status.name());

        assertThatCode(() -> insertReview(orderItemId, status.name(), 5, 5, 5, 5))
                .doesNotThrowAnyException();
    }

    /**
     * 평점 범위 밖의 값이 거절되는지 네 컬럼 각각에서 확인한다(DOMAIN.md 2.2, PLAN.md R5).
     *
     * <p>컬럼이 {@code TINYINT UNSIGNED} 라 제약이 없으면 0 과 255 가 그대로 들어간다. 화면의
     * 별점 입력은 폼을 거친 요청만 막으므로 API 를 직접 부르면 통과한다. 평점은
     * {@code products.average_rating} 의 근거라 <b>범위 밖 값 하나가 그 상품의 평균을 통째로
     * 무너뜨리는데</b>, 화면에는 그럴듯한 숫자가 그대로 보인다.
     *
     * <p>0 이 특히 새기 쉽다. 별을 하나도 안 누른 폼이 그대로 넘어오면 만들어지는 값이 0 이고,
     * 이 값은 "안 매김"과 구분되지 않는다.
     *
     * <p>네 컬럼을 모두 도는 이유는 제약을 컬럼마다 따로 걸었기 때문이다. 하나를 빠뜨려도 나머지
     * 셋이 통과해서 <b>테스트가 하나만 있으면 드러나지 않는다.</b>
     */
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

    /**
     * 후기 한 건을 넣기 위한 최소 데이터를 만들고 {@code order_items.id} 를 돌려준다.
     *
     * <p>{@code reviews} 가 {@code order_items} · {@code products} · {@code members} 세 곳에 FK 를
     * 걸고 있어 앞의 사슬이 없으면 아무것도 넣을 수 없다. {@code key} 를 받는 것은
     * {@code members.email} · {@code categories.code} · {@code orders.order_number} 가 각각
     * UNIQUE 여서다 — 테스트마다 다른 값을 넘긴다.
     *
     * <p>주문은 {@code PICKED_UP} 으로 만든다. 조각 0 의 제약 검증에는 상태가 필요 없지만, 후기가
     * 붙을 수 있는 유일한 주문 상태라 사슬을 실제 모양대로 둔다.
     */
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

    /**
     * 상품과 그 분류를 만든다.
     *
     * <p>{@code product_type} 이 {@code 'GENERAL'} 이면 {@code preparation_days} 가 0 이어야
     * 한다(chk_products_preparation_days_by_type). 기본값이 0 이라 넣지 않는다.
     */
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
