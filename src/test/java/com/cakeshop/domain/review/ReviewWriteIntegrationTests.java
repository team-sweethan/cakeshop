package com.cakeshop.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.error.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 후기 작성과 평점 집계를 실제 DB 로 확인한다(SPEC A3 · D1).
 *
 * <p>여기서 잡는 것들은 <b>단일 스레드 테스트로는 드러나지 않는다.</b> 순서를 뒤집어도
 * 요청이 하나면 결과가 똑같고, 집계 SELECT 가 잠금 읽기가 아니어도 동시 요청이 없으면
 * 값이 맞는다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class ReviewWriteIntegrationTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 후기를 쓰면 상품 평점과 후기 수가 같은 트랜잭션에서 갱신된다. */
    @Test
    void createReview_updatesProductRating() {
        Fixture fixture = insertPickedUpOrder("rating");

        reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 4));

        assertThat(averageRating(fixture.productId()))
                .isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(reviewCount(fixture.productId())).isEqualTo(1);
    }

    /**
     * 집계가 상품의 {@code updated_at} 을 건드리지 않는다.
     *
     * <p>보존하지 않으면 후기가 하나 달릴 때마다 상품에 수정 흔적이 남아 화면에
     * {@code (수정됨)} 이 붙는다. 커뮤니티에서 실제로 났던 버그다.
     */
    @Test
    void createReview_preservesProductUpdatedAt() {
        Fixture fixture = insertPickedUpOrder("updatedat");
        LocalDateTime before = productUpdatedAt(fixture.productId());

        reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 5));

        assertThat(productUpdatedAt(fixture.productId())).isEqualTo(before);
    }

    /** 남의 주문 상품은 403 이 아니라 404 다. 존재 자체를 알려 주지 않는다. */
    @Test
    void createReview_otherMembersOrderItem_isNotFound() {
        Fixture fixture = insertPickedUpOrder("owner");
        long otherMemberId = insertMember("intruder");

        assertThatThrownBy(() ->
                reviewService.createReview(otherMemberId, form(fixture.orderItemId(), 5)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);

        assertThat(reviewCount(fixture.productId())).isEqualTo(0);
    }

    /** 픽업 전 주문에는 쓸 수 없다. */
    @Test
    void createReview_notPickedUp_isRejected() {
        Fixture fixture = insertOrder("ready", "READY_FOR_PICKUP");

        assertThatThrownBy(() ->
                reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 5)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_PICKED_UP);
    }

    /** 같은 주문 상품에 두 번은 쓸 수 없다. */
    @Test
    void createReview_twice_isRejected() {
        Fixture fixture = insertPickedUpOrder("twice");
        reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 5));

        assertThatThrownBy(() ->
                reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED);

        assertThat(reviewCount(fixture.productId())).isEqualTo(1);
    }

    /** 쓴 후기는 작성할 후기 목록에서 빠진다. */
    @Test
    void getWritableReviews_excludesWrittenOnes() {
        Fixture fixture = insertPickedUpOrder("writable");

        assertThat(reviewService.getWritableReviews(
                fixture.memberId(), new PageRequest(1, 20)).getTotalElements())
                .isEqualTo(1);

        reviewService.createReview(fixture.memberId(), form(fixture.orderItemId(), 5));

        assertThat(reviewService.getWritableReviews(
                fixture.memberId(), new PageRequest(1, 20)).getTotalElements())
                .isEqualTo(0);
    }

    /**
     * 같은 상품에 후기 두 건이 <b>동시에</b> 들어와도 집계가 어긋나지 않는다.
     *
     * <p>이 테스트가 잡는 것은 둘이다.
     *
     * <ul>
     *   <li><b>잠금 순서</b> — 저장한 뒤에 잠그면 INSERT 의 FK 확인이 건 공유 잠금을 집계가
     *       배타로 승격하려 해 교착이다.</li>
     *   <li><b>집계 SELECT 의 잠금 읽기</b> — 평범한 SELECT 면 트랜잭션 첫 SELECT 에서
     *       스냅샷이 굳어, 뒤엣것이 먼저 커밋된 후기를 못 보고 {@code review_count} 를 1 로
     *       덮어쓴다.</li>
     * </ul>
     *
     * <p>둘 다 순서대로 부르면 결과가 똑같아 <b>단일 스레드로는 통과한다.</b>
     */
    @Test
    void createReview_concurrentOnSameProduct_keepsCountConsistent() throws Exception {
        long productId = insertProduct("concurrent");
        Fixture first = insertPickedUpOrderFor(productId, "concurrent-a");
        Fixture second = insertPickedUpOrderFor(productId, "concurrent-b");

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (Fixture fixture : List.of(first, second)) {
                pool.submit(() -> {
                    try {
                        start.await();
                        reviewService.createReview(
                                fixture.memberId(), form(fixture.orderItemId(), 4));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures.get()).isZero();
        assertThat(reviewCount(productId)).isEqualTo(2);
        assertThat(averageRating(productId)).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    // ---------- 조회 helper ----------

    private BigDecimal averageRating(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT average_rating FROM products WHERE id = ?", BigDecimal.class, productId);
    }

    private long reviewCount(long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT review_count FROM products WHERE id = ?", Long.class, productId);
        return count == null ? 0 : count;
    }

    private LocalDateTime productUpdatedAt(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM products WHERE id = ?",
                LocalDateTime.class, productId);
    }

    // ---------- fixture ----------

    private record Fixture(long memberId, long productId, long orderItemId) {
    }

    private ReviewForm form(long orderItemId, int rating) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(orderItemId);
        form.setOverallRating(rating);
        form.setTasteRating(rating);
        form.setDesignRating(rating);
        form.setServiceRating(rating);
        form.setContent("맛있게 잘 먹었습니다. 다음에 또 주문할게요.");
        return form;
    }

    private Fixture insertPickedUpOrder(String key) {
        return insertOrder(key, "PICKED_UP");
    }

    private Fixture insertOrder(String key, String status) {
        long productId = insertProduct(key);
        return insertOrderFor(productId, key, status);
    }

    private Fixture insertPickedUpOrderFor(long productId, String key) {
        return insertOrderFor(productId, key, "PICKED_UP");
    }

    private Fixture insertOrderFor(long productId, String key, String status) {
        long memberId = insertMember(key);

        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, final_amount,
                    status, pickup_at, picked_up_at, created_at, updated_at
                )
                VALUES (?, ?, 'GENERAL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "ORD-" + key, memberId, "주문자", "010-0000-0000",
                "수령인", "010-0000-0000", new BigDecimal("30000"), new BigDecimal("30000"),
                status, CREATED_AT,
                "PICKED_UP".equals(status) ? CREATED_AT : null,
                CREATED_AT, CREATED_AT);
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?", Long.class, "ORD-" + key);

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
        Long orderItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);

        return new Fixture(memberId, productId, orderItemId);
    }

    private long insertMember(String key) {
        String email = key + "@cakeshop.local";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                email, "encoded-password", "리뷰-" + key, "010-0000-0000",
                "리뷰 테스트", CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct(String key) {
        jdbcTemplate.update(
                """
                INSERT INTO categories (code, name, sort_order, created_at, updated_at)
                VALUES (?, ?, 0, ?, ?)
                """,
                "CAT-" + key, "테스트 분류", CREATED_AT, CREATED_AT);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, "CAT-" + key);

        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, base_price, product_type, status, created_at, updated_at
                )
                VALUES (?, ?, ?, 'GENERAL', 'ACTIVE', ?, ?)
                """,
                categoryId, "케이크-" + key, new BigDecimal("30000"), CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?", Long.class, "케이크-" + key);
    }
}
