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

@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class ReviewScreenRenderingTests {

    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime WRITTEN_AT = LocalDateTime.of(2026, 8, 5, 9, 0);

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
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("id=\"review-images\"")))
                .andExpect(content().string(containsString("후기당 3장까지")))
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
                .andExpect(redirectedUrl("/mypage/reviews"));

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

    @Test
    void productDetail_moreThanThreeReviews_rendersOnlyTheLatestThreeWithFullListLink()
            throws Exception {

        insertReviewWithContent("가장 오래된 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewWithContent("세 번째 후기입니다.", "PUBLISHED", WRITTEN_AT.plusDays(1));
        insertReviewWithContent("두 번째 후기입니다.", "PUBLISHED", WRITTEN_AT.plusDays(2));
        insertReviewWithContent("가장 최신 후기입니다.", "PUBLISHED", WRITTEN_AT.plusDays(3));

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가장 최신 후기입니다.")))
                .andExpect(content().string(containsString("두 번째 후기입니다.")))
                .andExpect(content().string(containsString("세 번째 후기입니다.")))
                .andExpect(content().string(not(containsString("가장 오래된 후기입니다."))))
                .andExpect(content().string(
                        containsString("/products/" + productId + "/reviews")));
    }

    @Test
    void productDetail_noPublishedReview_hidesTheFullListLink() throws Exception {
        insertReviewWithContent("숨겨진 후기입니다.", "BLOCKED", WRITTEN_AT);

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 등록된 후기가 없습니다.")))
                .andExpect(content().string(not(containsString("숨겨진 후기입니다."))))
                .andExpect(content().string(not(containsString("전체 리뷰 확인"))));
    }

    @Test
    void productReviews_anonymousVisitor_seesPublishedReviewsAndAuthorNickname()
            throws Exception {

        insertReviewWithContent("공개된 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewWithContent("숨겨진 후기입니다.", "BLOCKED", WRITTEN_AT);
        insertReviewWithContent("지워진 후기입니다.", "DELETED", WRITTEN_AT);

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공개된 후기입니다.")))
                .andExpect(content().string(containsString("닉" + suffix)))
                .andExpect(content().string(not(containsString("숨겨진 후기입니다."))))
                .andExpect(content().string(not(containsString("지워진 후기입니다."))));
    }

    @Test
    void productReviewPreviewAndFullList_renderAttachedImagesInOrder() throws Exception {
        insertReviewWithContent("이미지가 있는 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewImageOn("이미지가 있는 후기입니다.", "/uploads/review/second.jpg", 1);
        insertReviewImageOn("이미지가 있는 후기입니다.", "/uploads/review/first.jpg", 0);

        for (String path : new String[]{
                "/products/" + productId,
                "/products/" + productId + "/reviews"}) {

            String body = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("/uploads/review/first.jpg");
            assertThat(body).contains("/uploads/review/second.jpg");
            assertThat(body.indexOf("/uploads/review/first.jpg"))
                    .isLessThan(body.indexOf("/uploads/review/second.jpg"));
        }
    }

    @Test
    void productReviews_repliedReview_showsTheOwnerReplyOnlyUnderThatReview() throws Exception {
        insertReviewWithContent("답글이 달릴 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewWithContent("답글이 없는 후기입니다.", "PUBLISHED", WRITTEN_AT.plusDays(1));
        insertReplyOn("답글이 달릴 후기입니다.", "찾아 주셔서 감사합니다.");

        String body = mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("찾아 주셔서 감사합니다.");
        assertThat(body.split("사장님 답글", -1)).hasSize(2);
    }

    @Test
    void productDetail_repliedReview_showsTheOwnerReplyInThePreview() throws Exception {
        insertReviewWithContent("미리보기에 뜰 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReplyOn("미리보기에 뜰 후기입니다.", "미리보기에도 보여야 합니다.");

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사장님 답글")))
                .andExpect(content().string(containsString("미리보기에도 보여야 합니다.")));
    }

    @Test
    void productReviews_noPublishedReview_rendersEmptyNotice() throws Exception {
        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 등록된 후기가 없습니다.")));
    }

    @Test
    void productReviews_withdrawnAuthor_isMaskedButTheReviewRemains() throws Exception {
        insertReviewWithContent("탈퇴한 사람의 후기입니다.", "PUBLISHED", WRITTEN_AT);
        jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", memberId);

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("탈퇴한 사람의 후기입니다.")))
                .andExpect(content().string(containsString("탈퇴한 회원")))
                .andExpect(content().string(not(containsString("닉" + suffix))));
    }

    @Test
    void productReviews_inactiveProduct_is404LikeAMissingOne() throws Exception {
        insertReviewWithContent("내린 상품의 후기입니다.", "PUBLISHED", WRITTEN_AT);
        jdbcTemplate.update("UPDATE products SET status = 'INACTIVE' WHERE id = ?", productId);

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("내린 상품의 후기입니다."))));

        mockMvc.perform(get("/products/{id}/reviews", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void productReviews_scriptInContent_isEscaped() throws Exception {
        insertReviewWithContent(
                "<script>alert('xss')</script> 맛있게 먹었습니다.", "PUBLISHED", WRITTEN_AT);

        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void myReviews_blockedReviewIsShownWithProductNameAndDeletedIsHidden() throws Exception {
        insertReviewWithContent("공개된 내 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewWithContent("숨겨진 내 후기입니다.", "BLOCKED", WRITTEN_AT);
        insertReviewWithContent("지워진 내 후기입니다.", "DELETED", WRITTEN_AT);

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공개된 내 후기입니다.")))
                .andExpect(content().string(containsString("숨겨진 내 후기입니다.")))
                .andExpect(content().string(containsString("숨김")))
                .andExpect(content().string(containsString("딸기 생크림 케이크")))
                .andExpect(content().string(
                        containsString("/products/" + productId + "/reviews")))
                .andExpect(content().string(not(containsString("지워진 내 후기입니다."))));
    }

    @Test
    void myReviews_repliedReview_showsTheOwnerReply() throws Exception {
        insertReviewWithContent("답글 받은 내 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReplyOn("답글 받은 내 후기입니다.", "다음에도 찾아 주세요.");

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사장님 답글")))
                .andExpect(content().string(containsString("다음에도 찾아 주세요.")));
    }

    @Test
    void myReviews_attachedImage_isRendered() throws Exception {
        insertReviewWithContent("이미지를 올린 내 후기입니다.", "PUBLISHED", WRITTEN_AT);
        insertReviewImageOn(
                "이미지를 올린 내 후기입니다.", "/uploads/review/my-review.jpg", 0);

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/uploads/review/my-review.jpg")));
    }

    @Test
    void myReviews_blockedReview_hidesTheOwnerReplyWithIt() throws Exception {
        insertReviewWithContent("숨겨진 내 후기입니다.", "BLOCKED", WRITTEN_AT);
        insertReplyOn("숨겨진 내 후기입니다.", "숨긴 뒤에는 보이면 안 됩니다.");

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("숨겨진 내 후기입니다.")))
                .andExpect(content().string(not(containsString("사장님 답글"))))
                .andExpect(content().string(not(containsString("숨긴 뒤에는 보이면 안 됩니다."))));
    }

    @Test
    void myReviews_otherMembersReview_isNotListed() throws Exception {
        insertReviewWithContent("남의 후기입니다.", "PUBLISHED", WRITTEN_AT);
        memberId = insertOtherMember();

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성한 후기가 없습니다.")))
                .andExpect(content().string(not(containsString("남의 후기입니다."))));
    }

    @Test
    void myReviews_publishedReview_rendersEditAndDeleteWithTheRewriteWarning() throws Exception {
        insertReviewWithContent("공개된 내 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/reviews/" + reviewId + "/edit")))
                .andExpect(content().string(containsString("/reviews/" + reviewId + "/delete")))
                .andExpect(content().string(containsString(
                        "삭제하면 이 주문 상품에는 다시 후기를 작성할 수 없습니다.")));
    }

    @Test
    void myReviews_blockedReview_hidesEditAndDeleteBecauseNothingCanBeDoneToIt()
            throws Exception {

        insertReviewWithContent("숨겨진 내 후기입니다.", "BLOCKED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("숨겨진 내 후기입니다.")))
                .andExpect(content().string(not(containsString("/reviews/" + reviewId + "/edit"))))
                .andExpect(content().string(
                        not(containsString("/reviews/" + reviewId + "/delete"))));
    }

    @Test
    void editForm_ownPublishedReview_rendersSavedValuesWithoutOrderItemSelection()
            throws Exception {

        insertReviewWithContent("고치기 전 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);

        mockMvc.perform(get("/reviews/{id}/edit", reviewId).with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("고치기 전 후기입니다.")))
                .andExpect(content().string(containsString("딸기 생크림 케이크")))
                .andExpect(content().string(containsString("id=\"overall-rating\"")))
                .andExpect(content().string(not(containsString("name=\"orderItemId\""))));
    }

    @Test
    void editForm_othersReview_is404LikeAMissingOne() throws Exception {
        insertReviewWithContent("남의 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);
        memberId = insertOtherMember();

        mockMvc.perform(get("/reviews/{id}/edit", reviewId).with(authentication(login())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("남의 후기입니다."))));
    }

    @Test
    void editForm_blockedReview_is403SoTheAuthorLearnsItWasHidden() throws Exception {
        insertReviewWithContent("숨겨진 후기입니다.", "BLOCKED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);

        mockMvc.perform(get("/reviews/{id}/edit", reviewId).with(authentication(login())))
                .andExpect(status().isForbidden());
    }

    @Test
    void edit_validForm_savesNewValuesAndKeepsTheOrderItem() throws Exception {
        insertReviewWithContent("고치기 전 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);
        long orderItemId = jdbcTemplate.queryForObject(
                "SELECT order_item_id FROM reviews WHERE id = ?", Long.class, reviewId);

        mockMvc.perform(post("/reviews/{id}/edit", reviewId)
                        .with(authentication(login()))
                        .with(csrf())
                        .param("overallRating", "3")
                        .param("tasteRating", "3")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "다시 먹어 보고 평점을 고쳤습니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("다시 먹어 보고 평점을 고쳤습니다.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_item_id FROM reviews WHERE id = ?", Long.class, reviewId))
                .isEqualTo(orderItemId);
    }

    @Test
    void delete_ownReview_hidesItFromEveryListButKeepsTheRow() throws Exception {
        insertReviewWithContent("지울 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);

        mockMvc.perform(post("/reviews/{id}/delete", reviewId)
                        .with(authentication(login()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("DELETED");

        mockMvc.perform(get("/mypage/reviews").with(authentication(login())))
                .andExpect(content().string(not(containsString("지울 후기입니다."))));
        mockMvc.perform(get("/products/{id}/reviews", productId))
                .andExpect(content().string(not(containsString("지울 후기입니다."))));
    }

    @Test
    void delete_othersReview_is404AndLeavesTheReviewAlone() throws Exception {
        insertReviewWithContent("남의 후기입니다.", "PUBLISHED", WRITTEN_AT);
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE member_id = ?", Long.class, memberId);
        memberId = insertOtherMember();

        mockMvc.perform(post("/reviews/{id}/delete", reviewId)
                        .with(authentication(login()))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("PUBLISHED");
    }

    private void insertReviewWithContent(
            String content, String status, LocalDateTime createdAt) {

        long orderItemId = insertPickedUpOrderItem("딸기 생크림 케이크");
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating,
                    content, status, created_at
                ) VALUES (?, ?, ?, 5, 5, 4, 4, ?, ?, ?)
                """,
                orderItemId,
                productId,
                memberId,
                content,
                status,
                createdAt);
    }

    private void insertReplyOn(String reviewContent, String replyContent) {
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE content = ?", Long.class, reviewContent);

        jdbcTemplate.update(
                "INSERT INTO review_replies (review_id, admin_id, content) VALUES (?, ?, ?)",
                reviewId,
                insertAdmin(),
                replyContent);
    }

    private void insertReviewImageOn(String reviewContent, String imageUrl, int sortOrder) {
        long reviewId = jdbcTemplate.queryForObject(
                "SELECT id FROM reviews WHERE content = ?", Long.class, reviewContent);

        jdbcTemplate.update(
                "INSERT INTO review_images (review_id, image_url, sort_order) VALUES (?, ?, ?)",
                reviewId,
                imageUrl,
                sortOrder);
    }

    private long insertAdmin() {
        String email = "review-screen-admin-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '관리자', ?, '010-0000-0000', 'ADMIN', 'ACTIVE')
                """,
                email,
                "관리닉" + System.nanoTime());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
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

    private long insertOtherMember() {
        String email = "review-screen-other-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', '다른사람', ?, '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email,
                "다른닉" + System.nanoTime());
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
                    base_price, option_amount, total_amount, preparation_days
                ) VALUES (?, ?, ?, 'GENERAL', 1, 20000, 0, 20000, 0)
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
