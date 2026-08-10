package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.global.config.MariaDbIntegrationTest;

@SpringBootTest
@MariaDbIntegrationTest
class ReviewRatingAggregationTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProductReviewCommandService productReviewCommandService;

    private ExecutorService executor;
    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        memberId = insertMember();
        productId = insertProduct();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        jdbcTemplate.update("DELETE FROM reviews WHERE member_id = ?", memberId);
    }

    @Test
    void write_singleReview_storesMatchingAggregateOnProduct() {
        long orderItemId = insertPickedUpOrderItem();

        reviewService.write(form(orderItemId, 4), memberId);

        assertThat(averageRating()).isEqualByComparingTo("4.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void write_concurrentReviewsOnSameProduct_countMatchesStoredRows() throws Exception {
        long firstOrderItemId = insertPickedUpOrderItem();
        long secondOrderItemId = insertPickedUpOrderItem();

        List<Future<Void>> results = executor.invokeAll(List.of(
                writeTask(firstOrderItemId, 5),
                writeTask(secondOrderItemId, 3)));

        for (Future<Void> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }

        long storedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE product_id = ? AND status = 'PUBLISHED'",
                Long.class,
                productId);

        assertThat(reviewCount()).isEqualTo(storedRows).isEqualTo(2);
        assertThat(averageRating()).isEqualByComparingTo("4.00");
    }

    @Test
    void write_aggregateFails_rollsBackTheReviewToo() {
        long orderItemId = insertPickedUpOrderItem();
        doThrow(new IllegalStateException("집계 실패"))
                .when(productReviewCommandService)
                .applyReviewAggregate(anyLong(), any(), anyLong());

        assertThatThrownBy(() -> reviewService.write(form(orderItemId, 5), memberId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", Long.class, orderItemId))
                .isZero();
    }

    private Callable<Void> writeTask(long orderItemId, int rating) {
        return () -> {
            reviewService.write(form(orderItemId, rating), memberId);
            return null;
        };
    }

    private BigDecimal averageRating() {
        return jdbcTemplate.queryForObject(
                "SELECT average_rating FROM products WHERE id = ?", BigDecimal.class, productId);
    }

    private long reviewCount() {
        return jdbcTemplate.queryForObject(
                "SELECT review_count FROM products WHERE id = ?", Long.class, productId);
    }

    private ReviewWriteForm form(long orderItemId, int rating) {
        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(orderItemId);
        form.setOverallRating(rating);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("맛있게 잘 먹었습니다. 다음에도 주문할게요.");
        return form;
    }

    private long insertMember() {
        String email = "rating-aggregation-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '작성자', ?, '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email,
                "닉" + System.nanoTime());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct() {
        String unique = String.valueOf(System.nanoTime());
        String categoryCode = "RATING_AGG_" + unique;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "집계 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = "집계 대상 상품 " + unique;
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

    private long insertPickedUpOrderItem() {
        String orderNumber = "RATING-AGG-" + System.nanoTime();
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, discount_amount,
                    final_amount, status, pickup_at, picked_up_at
                ) VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222', '수령자',
                          '010-3333-4444', 20000, 0, 20000, 'PICKED_UP', ?, ?)
                """,
                orderNumber,
                memberId,
                PICKED_UP_AT.minusDays(1),
                PICKED_UP_AT);
        long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?", Long.class, orderNumber);

        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type, quantity,
                    base_price, option_amount, total_amount, preparation_days,
                    cancellation_limit_days
                ) VALUES (?, ?, '집계 대상 케이크', 'GENERAL', 1, 20000, 0, 20000, 0, 0)
                """,
                orderId,
                productId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }
}
