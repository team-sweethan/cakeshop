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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

/**
 * 후기 작성 화면을 실제 Thymeleaf로 렌더링한다.
 *
 * <p>이 클래스가 소유하는 것은 템플릿별 대표 렌더링, 사용자 입력 escaping, 그리고 화면 결과가
 * 실질적으로 달라지는 상태(A1의 빈 목록과 한 건)다. 자격 검증 규칙은
 * {@code ReviewServiceTests}, 요청 바인딩과 검증 거부는 {@code ReviewControllerTests},
 * SQL 결과는 {@code ReviewMapperTests}가 본다.
 */
@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class ReviewScreenRenderingTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String suffix;
    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        suffix = String.valueOf(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct("딸기 생크림 케이크");
    }

    @Test
    void writableList_noWritableOrderItem_rendersEmptyNotice() throws Exception {
        mockMvc.perform(get("/mypage/reviews/writable").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성할 후기가 없습니다.")))
                .andExpect(content().string(not(containsString("후기 쓰기"))));
    }

    @Test
    void writableList_pickedUpOrderItem_rendersWriteLinkWithOrderItemId() throws Exception {
        long orderItemId = insertPickedUpOrderItem("딸기 생크림 케이크");

        mockMvc.perform(get("/mypage/reviews/writable").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("딸기 생크림 케이크")))
                .andExpect(content().string(
                        containsString("/reviews/new?orderItemId=" + orderItemId)));
    }

    @Test
    void writableList_pageBeyondLastPage_stillShowsEmptyNotice() throws Exception {
        insertPickedUpOrderItem("딸기 생크림 케이크");

        mockMvc.perform(get("/mypage/reviews/writable")
                        .param("page", "2")
                        .with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성할 후기가 없습니다.")));
    }

    @Test
    void screenCatalog_reviewEntry_pointsToWritableListNotTheFormWithoutParameter()
            throws Exception {

        mockMvc.perform(get("/screens"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/mypage/reviews/writable")))
                .andExpect(content().string(not(containsString("\"/reviews/new\""))));
    }

    @Test
    void writableList_alreadyReviewedOrderItem_disappearsFromList() throws Exception {
        long orderItemId = insertPickedUpOrderItem("이미 쓴 케이크");
        insertReview(orderItemId);

        mockMvc.perform(get("/mypage/reviews/writable").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성할 후기가 없습니다.")));
    }

    @Test
    void form_writableOrderItem_rendersTargetAndRatingInputs() throws Exception {
        long orderItemId = insertPickedUpOrderItem("딸기 생크림 케이크");

        mockMvc.perform(get("/reviews/new")
                        .param("orderItemId", String.valueOf(orderItemId))
                        .with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("딸기 생크림 케이크")))
                .andExpect(content().string(containsString("id=\"overall-rating\"")))
                .andExpect(content().string(containsString("id=\"service-rating\"")))
                .andExpect(content().string(not(containsString("포장"))))
                .andExpect(content().string(not(containsString("이미지 첨부"))))
                .andExpect(content().string(not(containsString("삭제하기"))))
                .andExpect(content().string(not(containsString("order_items"))));
    }

    @Test
    void write_scriptInContent_isEscapedOnRerenderedForm() throws Exception {
        long orderItemId = insertPickedUpOrderItem("딸기 생크림 케이크");

        mockMvc.perform(post("/reviews")
                        .with(authentication(login()))
                        .with(csrf())
                        .param("orderItemId", String.valueOf(orderItemId))
                        .param("overallRating", "0")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "<script>alert('xss')</script> 맛있게 먹었습니다."))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void write_validForm_persistsReviewAndRedirects() throws Exception {
        long orderItemId = insertPickedUpOrderItem("딸기 생크림 케이크");

        mockMvc.perform(post("/reviews")
                        .with(authentication(login()))
                        .with(csrf())
                        .param("orderItemId", String.valueOf(orderItemId))
                        .param("overallRating", "5")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews/writable"));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM reviews WHERE order_item_id = ?",
                                String.class,
                                orderItemId))
                .isEqualTo("PUBLISHED");

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT product_id FROM reviews WHERE order_item_id = ?",
                                Long.class,
                                orderItemId))
                .isEqualTo(productId);
    }

    private Authentication login() {
        MemberDetails memberDetails = new MemberDetails(new MemberAuthenticationView(
                memberId, "review-screen-" + suffix + "@example.com", "encoded", "USER", true));

        return new UsernamePasswordAuthenticationToken(
                memberDetails, null, memberDetails.getAuthorities());
    }

    private long insertMember() {
        String email = "review-screen-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '작성자', ?, '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email,
                "닉" + suffix);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long insertProduct(String name) {
        String categoryCode = "REVIEW_SCREEN_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "후기 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = name + " " + suffix;
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

    private long insertPickedUpOrderItem(String productName) {
        String orderNumber = "REVIEW-SCREEN-" + System.nanoTime();
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
                ) VALUES (?, ?, ?, 'GENERAL', 1, 20000, 0, 20000, 0, 0)
                """,
                orderId,
                productId,
                productName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
    }

    private void insertReview(long orderItemId) {
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating,
                    content, status
                ) VALUES (?, ?, ?, 5, 5, 5, 5, '이미 쓴 후기입니다.', 'PUBLISHED')
                """,
                orderItemId,
                productId,
                memberId);
    }
}
