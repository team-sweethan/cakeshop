package com.cakeshop.domain.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.domain.review.dto.query.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.dto.command.ReviewUpdateCommand;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewMapperTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime WRITTEN_AT = LocalDateTime.of(2026, 8, 9, 12, 0);

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

    @Test
    void aggregateForUpdate_noPublishedReview_returnsZeroAverageNotNull() {
        long orderItemId = insertPickedUpOrderItem();
        reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "곧 숨겨질 후기입니다."));
        jdbcTemplate.update(
                "UPDATE reviews SET status = 'BLOCKED' WHERE order_item_id = ?", orderItemId);

        ProductRatingAggregate aggregate = reviewMapper.aggregateForUpdate(productId);

        assertThat(aggregate.averageRating()).isEqualByComparingTo("0");
        assertThat(aggregate.reviewCount()).isZero();
    }

    @Test
    void aggregateForUpdate_blockedAndDeletedReviews_areExcludedFromAverage() {
        insertReviewWithStatus(5, "PUBLISHED");
        insertReviewWithStatus(3, "PUBLISHED");
        insertReviewWithStatus(1, "BLOCKED");
        insertReviewWithStatus(1, "DELETED");

        ProductRatingAggregate aggregate = reviewMapper.aggregateForUpdate(productId);

        assertThat(aggregate.averageRating()).isEqualByComparingTo("4");
        assertThat(aggregate.reviewCount()).isEqualTo(2);
    }

    @Test
    void aggregateForUpdate_otherProductReviews_areNotCounted() {
        insertReviewWithStatus(5, "PUBLISHED");
        long otherProductId = insertProduct();

        assertThat(reviewMapper.aggregateForUpdate(otherProductId).reviewCount()).isZero();
    }

    @Test
    void findPublishedByProductId_blockedAndDeletedReviews_areNotListed() {
        insertReviewWithStatus(5, "PUBLISHED");
        insertReviewWithStatus(1, "BLOCKED");
        insertReviewWithStatus(1, "DELETED");

        List<ReviewRow> rows = reviewMapper.findPublishedByProductId(productId, 0, 20);

        assertThat(rows).singleElement()
                .extracting(ReviewRow::status)
                .isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(reviewMapper.countPublishedByProductId(productId)).isEqualTo(1L);
    }

    @Test
    void findPublishedByProductId_sameCreatedAt_isSplitAcrossPagesWithoutOverlap() {
        List<Long> insertedIds = insertPublishedReviewsAtSameInstant(3);

        List<Long> firstPage = idsOf(reviewMapper.findPublishedByProductId(productId, 0, 2));
        List<Long> secondPage = idsOf(reviewMapper.findPublishedByProductId(productId, 2, 2));

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
        assertThat(firstPage).containsExactlyElementsOf(
                insertedIds.stream().sorted(Comparator.reverseOrder()).limit(2).toList());
    }

    @Test
    void findPublishedByProductId_otherProductReviews_areNotListed() {
        insertReviewWithStatus(5, "PUBLISHED");
        long otherProductId = insertProduct();

        assertThat(reviewMapper.findPublishedByProductId(otherProductId, 0, 20)).isEmpty();
        assertThat(reviewMapper.countPublishedByProductId(otherProductId)).isZero();
    }

    @Test
    void findByMemberId_blockedReviewIsKeptAndDeletedIsHidden() {
        insertReviewWithStatus(5, "PUBLISHED");
        insertReviewWithStatus(4, "BLOCKED");
        insertReviewWithStatus(3, "DELETED");

        List<ReviewRow> rows = reviewMapper.findByMemberId(memberId, 0, 20);

        assertThat(rows).extracting(ReviewRow::status)
                .containsExactlyInAnyOrder(ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED);
        assertThat(reviewMapper.countByMemberId(memberId)).isEqualTo(2L);
    }

    @Test
    void findByMemberId_otherMemberReview_isNotListed() {
        insertReviewWithStatus(5, "PUBLISHED");
        long otherMemberId = insertMember();

        assertThat(reviewMapper.findByMemberId(otherMemberId, 0, 20)).isEmpty();
        assertThat(reviewMapper.countByMemberId(otherMemberId)).isZero();
    }

    @Test
    void findByMemberId_sameCreatedAt_isSplitAcrossPagesWithoutOverlap() {
        insertPublishedReviewsAtSameInstant(3);

        List<Long> firstPage = idsOf(reviewMapper.findByMemberId(memberId, 0, 2));
        List<Long> secondPage = idsOf(reviewMapper.findByMemberId(memberId, 2, 2));

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
    }

    @Test
    void findById_deletedReview_isStillReturnedSoTheCauseCanBeTold() {
        long orderItemId = insertPickedUpOrderItem();
        Review review = Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "지워질 후기입니다.");
        reviewMapper.insert(review);
        jdbcTemplate.update("UPDATE reviews SET status = 'DELETED' WHERE id = ?", review.getId());

        assertThat(reviewMapper.findById(review.getId()))
                .extracting(ReviewRow::status)
                .isEqualTo(ReviewStatus.DELETED);
    }

    @Test
    void findById_missingReview_isNull() {
        assertThat(reviewMapper.findById(Long.MAX_VALUE)).isNull();
    }

    @Test
    void update_publishedReview_changesRatingsAndContentOnly() {
        long orderItemId = insertPickedUpOrderItem();
        Review review = Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "고치기 전 후기입니다.");
        reviewMapper.insert(review);

        int affectedRows = reviewMapper.update(new ReviewUpdateCommand(
                review.getId(), memberId, 3, 2, 1, 4, "고친 뒤 후기입니다."));

        assertThat(affectedRows).isEqualTo(1);

        ReviewRow updated = reviewMapper.findById(review.getId());
        assertThat(updated.overallRating()).isEqualTo(3);
        assertThat(updated.tasteRating()).isEqualTo(2);
        assertThat(updated.designRating()).isEqualTo(1);
        assertThat(updated.serviceRating()).isEqualTo(4);
        assertThat(updated.content()).isEqualTo("고친 뒤 후기입니다.");
        assertThat(updated.orderItemId()).isEqualTo(orderItemId);
        assertThat(updated.productId()).isEqualTo(productId);
        assertThat(updated.memberId()).isEqualTo(memberId);
        assertThat(updated.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void update_blockedReview_affectsNoRowSoTheAuthorCannotOverwriteIt() {
        long reviewId = insertReviewWithStatus(5, "BLOCKED");

        assertThat(reviewMapper.update(new ReviewUpdateCommand(
                reviewId, memberId, 1, 1, 1, 1, "숨겨진 뒤 덮어쓴 후기입니다.")))
                .isZero();

        assertThat(reviewMapper.findById(reviewId).content()).isEqualTo("후기 본문입니다.");
    }

    @Test
    void update_otherMembersReview_affectsNoRow() {
        long reviewId = insertReviewWithStatus(5, "PUBLISHED");
        long otherMemberId = insertMember();

        assertThat(reviewMapper.update(new ReviewUpdateCommand(
                reviewId, otherMemberId, 1, 1, 1, 1, "남이 고쳐 쓴 후기입니다.")))
                .isZero();
    }

    @Test
    void update_ratingOutOfRange_isRejectedByCheckConstraint() {
        long reviewId = insertReviewWithStatus(5, "PUBLISHED");

        assertThatThrownBy(() -> reviewMapper.update(new ReviewUpdateCommand(
                reviewId, memberId, 6, 5, 4, 4, "범위 밖으로 고친 후기입니다.")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByAuthor_publishedReview_keepsTheRowSoRewritingStaysBlocked() {
        long orderItemId = insertPickedUpOrderItem();
        Review review = Review.create(
                orderItemId, productId, memberId, 5, 5, 4, 4, "지울 후기입니다.");
        reviewMapper.insert(review);

        assertThat(reviewMapper.deleteByAuthor(review.getId(), memberId)).isEqualTo(1);

        assertThat(reviewMapper.findById(review.getId()).status())
                .isEqualTo(ReviewStatus.DELETED);
        assertThatThrownBy(() -> reviewMapper.insert(Review.create(
                orderItemId, productId, memberId, 4, 4, 4, 4, "다시 쓴 후기입니다.")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deleteByAuthor_blockedReview_affectsNoRow() {
        long reviewId = insertReviewWithStatus(5, "BLOCKED");

        assertThat(reviewMapper.deleteByAuthor(reviewId, memberId)).isZero();
        assertThat(reviewMapper.findById(reviewId).status()).isEqualTo(ReviewStatus.BLOCKED);
    }

    @Test
    void deleteByAuthor_alreadyDeletedReview_affectsNoRow() {
        long reviewId = insertReviewWithStatus(5, "DELETED");

        assertThat(reviewMapper.deleteByAuthor(reviewId, memberId)).isZero();
    }

    @Test
    void deleteByAuthor_otherMembersReview_affectsNoRow() {
        long reviewId = insertReviewWithStatus(5, "PUBLISHED");
        long otherMemberId = insertMember();

        assertThat(reviewMapper.deleteByAuthor(reviewId, otherMemberId)).isZero();
        assertThat(reviewMapper.findById(reviewId).status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    private List<Long> idsOf(List<ReviewRow> rows) {
        return rows.stream().map(ReviewRow::id).toList();
    }

    private List<Long> insertPublishedReviewsAtSameInstant(int count) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long orderItemId = insertPickedUpOrderItem();
            Review review = Review.create(
                    orderItemId, productId, memberId, 5, 5, 4, 4, "같은 시각 후기입니다.");
            reviewMapper.insert(review);
            ids.add(review.getId());
        }
        jdbcTemplate.update(
                "UPDATE reviews SET created_at = ? WHERE product_id = ?",
                WRITTEN_AT,
                productId);
        return ids;
    }

    private long insertReviewWithStatus(int overallRating, String status) {
        long orderItemId = insertPickedUpOrderItem();
        Review review = Review.create(
                orderItemId, productId, memberId, overallRating, 5, 4, 4, "후기 본문입니다.");
        reviewMapper.insert(review);
        jdbcTemplate.update(
                "UPDATE reviews SET status = ? WHERE order_item_id = ?", status, orderItemId);
        return review.getId();
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
        String unique = String.valueOf(System.nanoTime());
        String categoryCode = "REVIEW_MAPPER_" + unique;
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
                    base_price, option_amount, total_amount, preparation_days
                ) VALUES (?, ?, '후기 대상 케이크', 'GENERAL', 1, 20000, 0, 20000, 0)
                """,
                orderId,
                productId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }
}
