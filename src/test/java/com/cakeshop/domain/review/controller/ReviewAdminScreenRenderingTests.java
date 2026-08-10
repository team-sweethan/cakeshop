package com.cakeshop.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.security.MemberDetails;

@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class ReviewAdminScreenRenderingTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String suffix;
    private long adminId;
    private long memberId;
    private String nickname;
    private long productId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        suffix = String.valueOf(System.nanoTime());
        nickname = "후기작성자" + suffix;
        adminId = insertMember("관리자" + suffix, "ADMIN");
        memberId = insertMember(nickname, "USER");
        productId = insertProduct();
    }

    @Test
    void list_rendersAuthorAndProductName_withoutTheMockupDeleteButton() throws Exception {
        insertReview("맛있게 잘 먹었습니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nickname)))
                .andExpect(content().string(containsString("딸기 생크림 케이크 " + suffix)))
                .andExpect(content().string(not(containsString("후기를 삭제하시겠습니까?"))));
    }

    @Test
    void list_blockedAndDeletedReviews_areStillListed() throws Exception {
        long blocked = insertReview("숨겨진 후기입니다.", "BLOCKED", 5);
        long deleted = insertReview("삭제된 후기입니다.", "DELETED", 5);

        mockMvc.perform(get("/admin/reviews")
                        .param("writer", nickname)
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/reviews/" + blocked)))
                .andExpect(content().string(containsString("/admin/reviews/" + deleted)))
                .andExpect(content().string(containsString("숨김")))
                .andExpect(content().string(containsString("삭제됨")));
    }

    @Test
    void list_writerSearch_keepsOnlyThatAuthor() throws Exception {
        long mine = insertReview("내 후기입니다.", "PUBLISHED", 5);
        long othersId = insertMember("다른작성자" + suffix, "USER");
        long others = insertReviewBy(othersId, "남의 후기입니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews")
                        .param("writer", nickname)
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/reviews/" + mine)))
                .andExpect(content().string(not(containsString("/admin/reviews/" + others))));
    }

    @Test
    void list_productSearch_keepsOnlyThatProduct() throws Exception {
        long strawberry = insertReview("딸기 후기입니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews")
                        .param("product", "딸기")
                        .param("writer", nickname)
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/reviews/" + strawberry)));

        mockMvc.perform(get("/admin/reviews")
                        .param("product", "초코")
                        .param("writer", nickname)
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 후기가 없습니다.")));
    }

    @Test
    void list_wildcardKeyword_doesNotMatchEverything() throws Exception {
        long reviewId = insertReview("전부 조회되면 안 됩니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews")
                        .param("writer", "%")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/reviews/" + reviewId))));

        mockMvc.perform(get("/admin/reviews")
                        .param("product", "_")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/reviews/" + reviewId))));
    }

    @Test
    void list_unknownRatingParameter_fallsBackToNoFilter() throws Exception {
        long reviewId = insertReview("평점 3점 후기입니다.", "PUBLISHED", 3);

        mockMvc.perform(get("/admin/reviews")
                        .param("writer", nickname)
                        .param("rating", "여섯점")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/reviews/" + reviewId)));
    }

    @Test
    void list_ratingFilter_appliesTheThreeOrLessRange() throws Exception {
        long low = insertReview("낮은 평점입니다.", "PUBLISHED", 2);
        long high = insertReview("높은 평점입니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews")
                        .param("writer", nickname)
                        .param("rating", "3")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/reviews/" + low)))
                .andExpect(content().string(not(containsString("/admin/reviews/" + high))));
    }

    @Test
    void detail_rendersEveryRatingAndTheOrderNumber() throws Exception {
        long reviewId = insertReview("본문 전문이 보여야 합니다.", "PUBLISHED", 5);

        mockMvc.perform(get("/admin/reviews/{id}", reviewId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("본문 전문이 보여야 합니다.")))
                .andExpect(content().string(containsString("주문번호")))
                .andExpect(content().string(containsString("디자인")))
                .andExpect(content().string(containsString("응대")))
                .andExpect(content().string(containsString("숨기기")))
                .andExpect(content().string(not(containsString("숨김 해제"))));
    }

    @Test
    void detail_missingReview_isNotFound() throws Exception {
        mockMvc.perform(get("/admin/reviews/{id}", 99999999L).with(authentication(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void block_thenDetail_offersUnblockInstead() throws Exception {
        long reviewId = insertReview("숨길 후기입니다.", "PUBLISHED", 5);

        mockMvc.perform(post("/admin/reviews/{id}/block", reviewId)
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(reviewId)).isEqualTo("BLOCKED");

        mockMvc.perform(get("/admin/reviews/{id}", reviewId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("숨김 해제")))
                .andExpect(content().string(not(containsString("숨기기"))));
    }

    @Test
    void unblock_returnsTheReviewToPublished() throws Exception {
        long reviewId = insertReview("되돌릴 후기입니다.", "BLOCKED", 5);

        mockMvc.perform(post("/admin/reviews/{id}/unblock", reviewId)
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(reviewId)).isEqualTo("PUBLISHED");
    }

    @Test
    void block_deletedReview_isRejectedAndTheDetailSaysSo() throws Exception {
        long reviewId = insertReview("작성자가 지운 후기입니다.", "DELETED", 5);

        mockMvc.perform(post("/admin/reviews/{id}/block", reviewId)
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(reviewId)).isEqualTo("DELETED");

        mockMvc.perform(get("/admin/reviews/{id}", reviewId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조치할 수 없습니다")));
    }

    @Test
    void screenCatalog_listsTheAdminReviewDetail() throws Exception {
        mockMvc.perform(get("/screens"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A15 후기 상세")))
                .andExpect(content().string(containsString("관리자 화면 15개")));
    }

    private String statusOf(long reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, reviewId);
    }

    private Authentication admin() {
        MemberDetails memberDetails = new MemberDetails(new MemberAuthenticationView(
                adminId, "review-admin-screen-" + suffix + "@example.com", "encoded",
                "ADMIN", true));

        return new UsernamePasswordAuthenticationToken(
                memberDetails, null, memberDetails.getAuthorities());
    }

    private long insertMember(String memberNickname, String role) {
        String email = "review-admin-screen-" + memberNickname + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '이름', ?, '010-0000-0000', ?, 'ACTIVE')
                """,
                email,
                memberNickname,
                role);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct() {
        String categoryCode = "REVIEW_ADMIN_SCREEN_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "후기 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = "딸기 생크림 케이크 " + suffix;
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

    private long insertReview(String content, String status, int overallRating) {
        return insertReviewBy(memberId, content, status, overallRating);
    }

    private long insertReviewBy(long authorId, String content, String status, int overallRating) {
        long orderItemId = insertPickedUpOrderItem(authorId);
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating,
                    content, status
                ) VALUES (?, ?, ?, ?, 5, 4, 4, ?, ?)
                """,
                orderItemId,
                productId,
                authorId,
                overallRating,
                content,
                status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE order_item_id = ?", Long.class, orderItemId);
    }

    private long insertPickedUpOrderItem(long ownerId) {
        String orderNumber = "REVIEW-ADMIN-SCREEN-" + System.nanoTime();
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
                ownerId,
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
                ) VALUES (?, ?, ?, 'GENERAL', 1, 20000, 0, 20000, 0, 0)
                """,
                orderId,
                productId,
                "딸기 생크림 케이크 " + suffix);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }
}
