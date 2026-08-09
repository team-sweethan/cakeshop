package com.cakeshop.domain.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.global.config.MariaDbIntegrationTest;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewMapperTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
    }

    @Test
    void insert_newReview_storesPublishedStatusAndGeneratedKey() {
        long orderItemId = insertPickedUpOrderItem();

        Review review = Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "맛있게 잘 먹었습니다.");
        reviewMapper.insert(review);

        assertThat(review.getId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, review.getId()))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void insert_secondReviewOnSameOrderItem_violatesUniqueKey() {
        long orderItemId = insertPickedUpOrderItem();
        reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "첫 번째 후기입니다."));

        assertThatThrownBy(() -> reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 4, 4, 4, 4, "두 번째 후기입니다.")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insert_ratingOutOfRange_isRejectedByCheckConstraint() {
        long orderItemId = insertPickedUpOrderItem();

        assertThatThrownBy(() -> reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 6, 5, 4, 4, "범위 밖 평점입니다.")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findReviewedOrderItemIds_deletedReview_isStillExcluded() {
        long publishedItemId = insertPickedUpOrderItem();
        long deletedItemId = insertPickedUpOrderItem();
        reviewMapper.insert(Review.create(
                publishedItemId, productId, memberId, 5, 5, 4, 4, "공개 후기입니다."));
        reviewMapper.insert(Review.create(
                deletedItemId, productId, memberId, 5, 5, 4, 4, "삭제된 후기입니다."));
        jdbcTemplate.update(
                "UPDATE reviews SET status = 'DELETED' WHERE order_item_id = ?", deletedItemId);

        assertThat(reviewMapper.findReviewedOrderItemIds(memberId))
                .containsExactlyInAnyOrder(publishedItemId, deletedItemId);
    }

    @Test
    void findReviewedOrderItemIds_otherMemberReview_isNotReturned() {
        long orderItemId = insertPickedUpOrderItem();
        reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "내 후기입니다."));

        long otherMemberId = insertMember();

        assertThat(reviewMapper.findReviewedOrderItemIds(otherMemberId)).isEmpty();
    }

    @Test
    void existsByOrderItemId_writtenOrderItem_isTrue() {
        long orderItemId = insertPickedUpOrderItem();

        assertThat(reviewMapper.existsByOrderItemId(orderItemId)).isFalse();

        reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "맛있게 잘 먹었습니다."));

        assertThat(reviewMapper.existsByOrderItemId(orderItemId)).isTrue();
    }

    private long insertMember() {
        String email = "review-mapper-" + System.nanoTime() + "@example.com";
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
        String categoryCode = "REVIEW_MAPPER_" + suffix;
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

    private long insertPickedUpOrderItem() {
        String orderNumber = "REVIEW-MAPPER-" + System.nanoTime();
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
