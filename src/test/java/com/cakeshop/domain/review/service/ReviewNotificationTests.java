package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.global.config.MariaDbIntegrationTest;

@SpringBootTest
@MariaDbIntegrationTest
class ReviewNotificationTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewAdminService reviewAdminService;

    // 스파이로 두지 않는다. Mockito 스파이는 실제 메서드를 프록시를 지나지 않고 부르므로
    // REQUIRES_NEW 가 사라지고, 커밋 이후 발송이 아무도 커밋하지 않는 트랜잭션에 얹힌다.
    @Autowired
    private ReviewNotificationSender reviewNotificationSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private NotificationService notificationService;

    @MockitoSpyBean
    private ProductReviewCommandService productReviewCommandService;

    private long memberId;
    private long firstAdminId;
    private long secondAdminId;
    private long suspendedAdminId;
    private long categoryId;
    private long productId;

    @BeforeEach
    void setUp() {
        memberId = insertMember("USER", "ACTIVE");
        firstAdminId = insertMember("ADMIN", "ACTIVE");
        secondAdminId = insertMember("ADMIN", "ACTIVE");
        suspendedAdminId = insertMember("ADMIN", "SUSPENDED");
        productId = insertProduct();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE receiver_id IN (?, ?, ?, ?)",
                memberId, firstAdminId, secondAdminId, suspendedAdminId);
        jdbcTemplate.update(
                "DELETE FROM review_replies WHERE review_id IN"
                        + " (SELECT id FROM reviews WHERE member_id = ?)",
                memberId);
        jdbcTemplate.update("DELETE FROM reviews WHERE member_id = ?", memberId);
        jdbcTemplate.update(
                "DELETE FROM order_items WHERE order_id IN"
                        + " (SELECT id FROM orders WHERE member_id = ?)",
                memberId);
        jdbcTemplate.update("DELETE FROM orders WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
        jdbcTemplate.update(
                "DELETE FROM members WHERE id IN (?, ?, ?, ?)",
                memberId, firstAdminId, secondAdminId, suspendedAdminId);
    }

    @Test
    void write_notifiesEveryActiveAdminAndSkipsTheSuspendedOne() {
        long reviewId = writeReview();

        assertThat(receiversOf("NEW_REVIEW", reviewId))
                .containsExactlyInAnyOrder(firstAdminId, secondAdminId);
        assertThat(actorOf("NEW_REVIEW", firstAdminId)).isEqualTo(memberId);
    }

    @Test
    void sendNewReview_calledAgainForTheSameReview_leavesOneNotificationPerAdmin() {
        long reviewId = writeReview();

        reviewNotificationSender.sendNewReview(reviewId, memberId);

        assertThat(countOf("NEW_REVIEW", firstAdminId)).isEqualTo(1);
        assertThat(countOf("NEW_REVIEW", secondAdminId)).isEqualTo(1);
    }

    @Test
    void reply_notifiesTheReviewAuthorAndNoAdmin() {
        long reviewId = writeReview();

        reviewAdminService.reply(reviewId, replyForm("감사합니다."), firstAdminId);

        assertThat(receiversOf("CUSTOMER_REVIEW", reviewId)).containsExactly(memberId);
        assertThat(actorOf("CUSTOMER_REVIEW", memberId)).isEqualTo(firstAdminId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_reply_id FROM notifications"
                        + " WHERE notification_type = 'CUSTOMER_REVIEW' AND receiver_id = ?",
                Long.class,
                memberId))
                .isEqualTo(replyIdOf(reviewId));
    }

    @Test
    void sendReviewReply_calledAgainForTheSameReply_leavesOneNotification() {
        long reviewId = writeReview();
        reviewAdminService.reply(reviewId, replyForm("감사합니다."), firstAdminId);

        reviewNotificationSender.sendReviewReply(
                reviewId, replyIdOf(reviewId), memberId, firstAdminId);

        assertThat(countOf("CUSTOMER_REVIEW", memberId)).isEqualTo(1);
    }

    @Test
    void write_notificationFails_keepsTheReviewAndDoesNotFailTheRequest() {
        doThrow(new IllegalStateException("알림 실패"))
                .when(notificationService)
                .makeNotification(any());
        long orderItemId = insertPickedUpOrderItem();

        reviewService.write(writeForm(orderItemId), memberId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", Long.class, orderItemId))
                .isEqualTo(1);
        assertThat(countOf("NEW_REVIEW", firstAdminId)).isZero();
    }

    @Test
    void write_rolledBack_sendsNoNotification() {
        doThrow(new IllegalStateException("집계 실패"))
                .when(productReviewCommandService)
                .applyReviewAggregate(anyLong(), any(), anyLong());
        long orderItemId = insertPickedUpOrderItem();

        assertThatThrownBy(() -> reviewService.write(writeForm(orderItemId), memberId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countOf("NEW_REVIEW", firstAdminId)).isZero();
        assertThat(countOf("NEW_REVIEW", secondAdminId)).isZero();
    }

    @Test
    void editEditReplyAndBlock_sendNoNotification() {
        long reviewId = writeReview();
        reviewAdminService.reply(reviewId, replyForm("감사합니다."), firstAdminId);
        long before = countOfAll();

        reviewService.edit(reviewId, editForm(), memberId);
        reviewAdminService.editReply(reviewId, replyForm("문구를 고쳤습니다."));
        reviewAdminService.block(reviewId);

        assertThat(countOfAll()).isEqualTo(before);
    }

    @Test
    void delete_sendsNoNotification() {
        long reviewId = writeReview();
        long before = countOfAll();

        reviewService.delete(reviewId, memberId);

        assertThat(countOfAll()).isEqualTo(before);
    }

    private long writeReview() {
        long orderItemId = insertPickedUpOrderItem();
        reviewService.write(writeForm(orderItemId), memberId);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE order_item_id = ?", Long.class, orderItemId);
    }

    private long replyIdOf(long reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM review_replies WHERE review_id = ?", Long.class, reviewId);
    }

    private List<Long> receiversOf(String type, long reviewId) {
        return jdbcTemplate.queryForList(
                "SELECT receiver_id FROM notifications"
                        + " WHERE notification_type = ? AND review_id = ?",
                Long.class,
                type,
                reviewId);
    }

    private long countOf(String type, long receiverId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications"
                        + " WHERE notification_type = ? AND receiver_id = ?",
                Long.class,
                type,
                receiverId);
    }

    private long countOfAll() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE receiver_id IN (?, ?, ?, ?)",
                Long.class,
                memberId, firstAdminId, secondAdminId, suspendedAdminId);
    }

    private long actorOf(String type, long receiverId) {
        return jdbcTemplate.queryForObject(
                "SELECT actor_id FROM notifications"
                        + " WHERE notification_type = ? AND receiver_id = ?",
                Long.class,
                type,
                receiverId);
    }

    private ReviewWriteForm writeForm(long orderItemId) {
        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(orderItemId);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("맛있게 잘 먹었습니다. 다음에도 주문할게요.");
        return form;
    }

    private ReviewEditForm editForm() {
        ReviewEditForm form = new ReviewEditForm();
        form.setOverallRating(4);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("다시 먹어 보고 평점을 고쳤습니다.");
        return form;
    }

    private ReviewReplyForm replyForm(String content) {
        ReviewReplyForm form = new ReviewReplyForm();
        form.setContent(content);
        return form;
    }

    private long insertMember(String role, String status) {
        String email = "review-notification-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '이름', ?, '010-0000-0000', ?, ?)
                """,
                email,
                "닉" + System.nanoTime(),
                role,
                status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct() {
        String unique = String.valueOf(System.nanoTime());
        String categoryCode = "REVIEW_NOTIFICATION_" + unique;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "후기 대상");
        categoryId = jdbcTemplate.queryForObject(
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
        String orderNumber = "REVIEW-NOTI-" + System.nanoTime();
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
