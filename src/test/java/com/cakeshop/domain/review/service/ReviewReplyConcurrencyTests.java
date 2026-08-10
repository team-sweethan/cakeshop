package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.error.BusinessException;

@SpringBootTest
@MariaDbIntegrationTest
class ReviewReplyConcurrencyTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private ReviewAdminService reviewAdminService;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private long adminId;
    private long memberId;
    private long productId;
    private long reviewId;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        adminId = insertMember("관리자", "ADMIN");
        memberId = insertMember("작성자", "USER");
        productId = insertProduct();
        reviewId = insertPublishedReview();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        jdbcTemplate.update("DELETE FROM notifications WHERE review_id = ?", reviewId);
        jdbcTemplate.update("DELETE FROM review_replies WHERE review_id = ?", reviewId);
        jdbcTemplate.update("DELETE FROM reviews WHERE id = ?", reviewId);

        // 이 관리자를 남기면 다른 테스트의 findActiveAdminIds 에 섞여 신규 후기 알림이 딸려
        // 생기고, 그쪽 정리가 알림 FK 에 걸린다.
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", adminId);
    }

    @Test
    void reply_behindACommittedBlock_isRejectedInsteadOfWritingOnABlockedReview() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            reviewMapper.findById(reviewId);

            blockInAnotherTransaction();

            reviewAdminService.reply(reviewId, form("숨긴 뒤에는 저장되면 안 됩니다."), adminId);

            return null;
        }))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.BLOCKED_REVIEW));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_replies WHERE review_id = ?", Long.class, reviewId))
                .isZero();
    }

    private void blockInAnotherTransaction() {
        try {
            executor.submit(() -> {
                reviewAdminService.block(reviewId);
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("숨김 처리를 커밋하지 못했다", e);
        }
    }

    private ReviewReplyForm form(String content) {
        ReviewReplyForm form = new ReviewReplyForm();
        form.setContent(content);
        return form;
    }

    private long insertMember(String name, String role) {
        String email = "review-reply-concurrency-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', ?, ?, '010-0000-0000', ?, 'ACTIVE')
                """,
                email,
                name,
                name + System.nanoTime(),
                role);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct() {
        String unique = String.valueOf(System.nanoTime());
        String categoryCode = "REVIEW_REPLY_CONCURRENCY_" + unique;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "후기 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = "후기 대상 상품 " + unique;
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

    private long insertPublishedReview() {
        long orderItemId = insertPickedUpOrderItem();
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating,
                    content, status
                ) VALUES (?, ?, ?, 5, 5, 4, 4, '답글을 기다리는 후기입니다.', 'PUBLISHED')
                """,
                orderItemId,
                productId,
                memberId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE order_item_id = ?", Long.class, orderItemId);
    }

    private long insertPickedUpOrderItem() {
        String orderNumber = "REVIEW-REPLY-" + System.nanoTime();
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
                ) VALUES (?, ?, '후기 대상 케이크', 'GENERAL', 1, 20000, 0, 20000, 0, 0)
                """,
                orderId,
                productId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }
}
