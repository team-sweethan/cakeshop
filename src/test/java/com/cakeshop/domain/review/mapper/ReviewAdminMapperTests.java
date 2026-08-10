package com.cakeshop.domain.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.domain.review.dto.view.AdminReviewFilter;
import com.cakeshop.domain.review.dto.view.AdminReviewRating;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewAdminMapperTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime WRITTEN_AT = LocalDateTime.of(2026, 8, 9, 12, 0);

    @Autowired
    private ReviewAdminMapper reviewAdminMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        memberId = insertMember();
        productId = insertProduct();
    }

    @Test
    void findForAdmin_showsEveryStatus() {
        long published = insertReview(5, ReviewStatus.PUBLISHED);
        long blocked = insertReview(5, ReviewStatus.BLOCKED);
        long deleted = insertReview(5, ReviewStatus.DELETED);

        List<ReviewRow> rows = findAll(mine(null, AdminReviewRating.ALL, null));

        assertThat(idsOf(rows)).containsExactlyInAnyOrder(published, blocked, deleted);
    }

    @Test
    void findForAdmin_emptyIdList_findsNothing() {
        insertReview(5, ReviewStatus.PUBLISHED);

        AdminReviewFilter filter =
                new AdminReviewFilter(List.of(), null, AdminReviewRating.ALL, null);

        assertThat(findAll(filter)).isEmpty();
        assertThat(reviewAdminMapper.countForAdmin(filter)).isZero();
    }

    @Test
    void findForAdmin_nullIdList_appliesNoWriterCondition() {
        long reviewId = insertReview(5, ReviewStatus.PUBLISHED);

        AdminReviewFilter filter =
                new AdminReviewFilter(null, null, AdminReviewRating.ALL, null);

        assertThat(idsOf(findAll(filter))).contains(reviewId);
    }

    @Test
    void findForAdmin_statusFilter_keepsOnlyThatStatus() {
        insertReview(5, ReviewStatus.PUBLISHED);
        long blocked = insertReview(5, ReviewStatus.BLOCKED);

        List<ReviewRow> rows =
                findAll(mine(null, AdminReviewRating.ALL, ReviewStatus.BLOCKED));

        assertThat(idsOf(rows)).containsExactly(blocked);
    }

    @Test
    void findForAdmin_fiveStarFilter_keepsOnlyFives() {
        long five = insertReview(5, ReviewStatus.PUBLISHED);
        insertReview(4, ReviewStatus.PUBLISHED);

        assertThat(idsOf(findAll(mine(null, AdminReviewRating.FIVE, null))))
                .containsExactly(five);
    }

    @Test
    void findForAdmin_fourStarFilter_keepsOnlyFours() {
        insertReview(5, ReviewStatus.PUBLISHED);
        long four = insertReview(4, ReviewStatus.PUBLISHED);

        assertThat(idsOf(findAll(mine(null, AdminReviewRating.FOUR, null))))
                .containsExactly(four);
    }

    @Test
    void findForAdmin_threeOrLessFilter_keepsEverythingBelowFour() {
        insertReview(5, ReviewStatus.PUBLISHED);
        insertReview(4, ReviewStatus.PUBLISHED);
        long three = insertReview(3, ReviewStatus.PUBLISHED);
        long one = insertReview(1, ReviewStatus.PUBLISHED);

        assertThat(idsOf(findAll(mine(null, AdminReviewRating.THREE_OR_LESS, null))))
                .containsExactlyInAnyOrder(three, one);
    }

    @Test
    void findForAdmin_bothIdConditions_intersect() {
        long first = insertReview(5, ReviewStatus.PUBLISHED);
        insertReview(5, ReviewStatus.PUBLISHED);

        long orderItemId = reviewMapper.findById(first).getOrderItemId();

        List<ReviewRow> rows =
                findAll(mine(List.of(orderItemId), AdminReviewRating.ALL, null));

        assertThat(idsOf(rows)).containsExactly(first);
    }

    @Test
    void findForAdmin_reviewsWrittenAtTheSameInstant_keepAStableOrderAcrossPages() {
        long first = insertReview(5, ReviewStatus.PUBLISHED);
        long second = insertReview(5, ReviewStatus.PUBLISHED);
        long third = insertReview(5, ReviewStatus.PUBLISHED);
        jdbcTemplate.update(
                "UPDATE reviews SET created_at = ? WHERE product_id = ?", WRITTEN_AT, productId);

        AdminReviewFilter filter = mine(null, AdminReviewRating.ALL, null);

        assertThat(idsOf(reviewAdminMapper.findForAdmin(filter, 0, 2)))
                .containsExactly(third, second);
        assertThat(idsOf(reviewAdminMapper.findForAdmin(filter, 2, 2)))
                .containsExactly(first);
        assertThat(reviewAdminMapper.countForAdmin(filter)).isEqualTo(3);
    }

    @Test
    void updateStatus_expectedStatusMatches_appliesTheTransition() {
        long reviewId = insertReview(5, ReviewStatus.PUBLISHED);

        assertThat(reviewAdminMapper.updateStatus(
                reviewId, ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED)).isEqualTo(1);
        assertThat(reviewMapper.findById(reviewId).getStatus()).isEqualTo(ReviewStatus.BLOCKED);
    }

    @Test
    void updateStatus_reviewAlreadyDeleted_affectsNoRow() {
        long reviewId = insertReview(5, ReviewStatus.DELETED);

        assertThat(reviewAdminMapper.updateStatus(
                reviewId, ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED)).isZero();
        assertThat(reviewMapper.findById(reviewId).getStatus()).isEqualTo(ReviewStatus.DELETED);
    }

    @Test
    void updateStatus_unblockingAPublishedReview_affectsNoRow() {
        long reviewId = insertReview(5, ReviewStatus.PUBLISHED);

        assertThat(reviewAdminMapper.updateStatus(
                reviewId, ReviewStatus.BLOCKED, ReviewStatus.PUBLISHED)).isZero();
    }

    private AdminReviewFilter mine(
            List<Long> orderItemIds, AdminReviewRating rating, ReviewStatus status) {
        return new AdminReviewFilter(List.of(memberId), orderItemIds, rating, status);
    }

    private List<ReviewRow> findAll(AdminReviewFilter filter) {
        return reviewAdminMapper.findForAdmin(filter, 0, 100);
    }

    private List<Long> idsOf(List<ReviewRow> rows) {
        return rows.stream().map(ReviewRow::getId).toList();
    }

    private long insertReview(int overallRating, ReviewStatus status) {
        long orderItemId = insertPickedUpOrderItem();
        Review review = Review.create(
                orderItemId, productId, memberId, overallRating, 5, 4, 4, "후기 본문입니다.");
        reviewMapper.insert(review);
        jdbcTemplate.update(
                "UPDATE reviews SET status = ? WHERE id = ?", status.name(), review.getId());
        return review.getId();
    }

    private long insertMember() {
        String email = "review-admin-mapper-" + System.nanoTime() + "@example.com";
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
        String categoryCode = "REVIEW_ADMIN_MAPPER_" + unique;
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

    private long insertPickedUpOrderItem() {
        String orderNumber = "REVIEW-ADMIN-" + System.nanoTime();
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
