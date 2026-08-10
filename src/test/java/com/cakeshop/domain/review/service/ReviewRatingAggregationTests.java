package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.error.BusinessException;

@SpringBootTest
@MariaDbIntegrationTest
class ReviewRatingAggregationTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewAdminService reviewAdminService;

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

    @Test
    void edit_changedOverallRating_movesTheStoredAverage() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);

        reviewService.edit(reviewIdOf(orderItemId), editForm(1), memberId);

        assertThat(averageRating()).isEqualByComparingTo("1.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void edit_unchangedOverallRating_stillRewritesTheAggregate() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 4), memberId);
        jdbcTemplate.update("UPDATE products SET average_rating = 0, review_count = 0 WHERE id = ?",
                productId);

        reviewService.edit(reviewIdOf(orderItemId), editForm(4), memberId);

        assertThat(averageRating())
                .as("평점이 그대로여도 재집계가 도는지 — 조건부 호출이면 0 이 남는다")
                .isEqualByComparingTo("4.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void delete_lastPublishedReview_dropsTheAverageToZero() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);

        reviewService.delete(reviewIdOf(orderItemId), memberId);

        assertThat(averageRating()).isEqualByComparingTo("0.00");
        assertThat(reviewCount()).isZero();
    }

    @Test
    void delete_oneOfTwoReviews_leavesTheOtherInTheAverage() {
        long firstOrderItemId = insertPickedUpOrderItem();
        long secondOrderItemId = insertPickedUpOrderItem();
        reviewService.write(form(firstOrderItemId, 5), memberId);
        reviewService.write(form(secondOrderItemId, 3), memberId);

        reviewService.delete(reviewIdOf(firstOrderItemId), memberId);

        assertThat(averageRating()).isEqualByComparingTo("3.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void delete_aggregateFails_rollsBackTheDeletionToo() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);
        doThrow(new IllegalStateException("집계 실패"))
                .when(productReviewCommandService)
                .applyReviewAggregate(anyLong(), any(), anyLong());

        assertThatThrownBy(() -> reviewService.delete(reviewId, memberId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void editAndDeleteAtTheSameTime_leaveTheAggregateMatchingTheStoredRows() throws Exception {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);

        List<Future<Void>> results = executor.invokeAll(List.of(
                rejectableTask(() -> reviewService.edit(reviewId, editForm(1), memberId)),
                rejectableTask(() -> reviewService.delete(reviewId, memberId))));

        for (Future<Void> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }

        long storedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE product_id = ? AND status = 'PUBLISHED'",
                Long.class,
                productId);

        assertThat(reviewCount()).isEqualTo(storedRows);
    }

    @Test
    void block_publishedReview_dropsItFromTheAggregate() {
        long firstOrderItemId = insertPickedUpOrderItem();
        long secondOrderItemId = insertPickedUpOrderItem();
        reviewService.write(form(firstOrderItemId, 5), memberId);
        reviewService.write(form(secondOrderItemId, 3), memberId);

        reviewAdminService.block(reviewIdOf(firstOrderItemId));

        assertThat(averageRating()).isEqualByComparingTo("3.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void unblock_blockedReview_bringsItBackIntoTheAggregate() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);
        reviewAdminService.block(reviewId);

        reviewAdminService.unblock(reviewId);

        assertThat(averageRating()).isEqualByComparingTo("5.00");
        assertThat(reviewCount()).isEqualTo(1);
    }

    @Test
    void deleteAndBlockAtTheSameTime_applyOnlyOneAndLeaveTheAggregateMatching() throws Exception {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);

        List<Future<Void>> results = executor.invokeAll(List.of(
                rejectableTask(() -> reviewService.delete(reviewId, memberId)),
                rejectableTask(() -> reviewAdminService.block(reviewId))));

        for (Future<Void> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }

        String finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, reviewId);

        assertThat(finalStatus)
                .as("나중 쓰기가 앞의 조치를 덮으면 안 된다 (DOMAIN.md 2.1)")
                .isIn("DELETED", "BLOCKED");
        assertThat(reviewCount()).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE product_id = ? AND status = 'PUBLISHED'",
                Long.class,
                productId));
    }

    @Test
    void block_waitingBehindDelete_reportsTheLatestDeletedState() throws Exception {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);
        CountDownLatch blockReachedProductLock = new CountDownLatch(1);
        CountDownLatch deletionCommitted = new CountDownLatch(1);
        AtomicBoolean pauseFirstLock = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (pauseFirstLock.compareAndSet(true, false)) {
                blockReachedProductLock.countDown();
                if (!deletionCommitted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("삭제 커밋을 기다리는 동안 시간 초과");
                }
            }
            return invocation.callRealMethod();
        }).when(productReviewCommandService).lockForRating(productId);

        Future<Void> blockResult = executor.submit(() -> {
            reviewAdminService.block(reviewId);
            return null;
        });
        assertThat(blockReachedProductLock.await(30, TimeUnit.SECONDS)).isTrue();

        try {
            reviewService.delete(reviewId, memberId);
        } finally {
            deletionCommitted.countDown();
        }

        assertThatThrownBy(() -> blockResult.get(30, TimeUnit.SECONDS))
                .isInstanceOfSatisfying(ExecutionException.class, exception ->
                        assertThat(exception.getCause())
                                .isInstanceOfSatisfying(BusinessException.class, businessException ->
                                        assertThat(businessException.getErrorCode())
                                                .isEqualTo(
                                                        ReviewErrorCode.INVALID_REVIEW_TRANSITION)));
    }

    @Test
    void edit_waitingBehindDelete_reportsTheLatestDeletedState() throws Exception {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(form(orderItemId, 5), memberId);
        long reviewId = reviewIdOf(orderItemId);
        CountDownLatch editReachedProductLock = new CountDownLatch(1);
        CountDownLatch deletionCommitted = new CountDownLatch(1);
        AtomicBoolean pauseFirstLock = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (pauseFirstLock.compareAndSet(true, false)) {
                editReachedProductLock.countDown();
                if (!deletionCommitted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("삭제 커밋을 기다리는 동안 시간 초과");
                }
            }
            return invocation.callRealMethod();
        }).when(productReviewCommandService).lockForRating(productId);

        Future<Void> editResult = executor.submit(() -> {
            reviewService.edit(reviewId, editForm(1), memberId);
            return null;
        });
        assertThat(editReachedProductLock.await(30, TimeUnit.SECONDS)).isTrue();

        try {
            reviewService.delete(reviewId, memberId);
        } finally {
            deletionCommitted.countDown();
        }

        assertThatThrownBy(() -> editResult.get(30, TimeUnit.SECONDS))
                .isInstanceOfSatisfying(ExecutionException.class, exception ->
                        assertThat(exception.getCause())
                                .isInstanceOfSatisfying(BusinessException.class, businessException ->
                                        assertThat(businessException.getErrorCode())
                                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND)));
    }

    private Callable<Void> rejectableTask(Runnable action) {
        return () -> {
            try {
                action.run();
            } catch (BusinessException ignored) {
            }
            return null;
        };
    }

    private long reviewIdOf(long orderItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE order_item_id = ?", Long.class, orderItemId);
    }

    private ReviewEditForm editForm(int rating) {
        ReviewEditForm form = new ReviewEditForm();
        form.setOverallRating(rating);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("다시 먹어 보고 평점을 고쳤습니다.");
        return form;
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
