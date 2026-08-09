package com.cakeshop.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.GlobalExceptionHandler;
import com.cakeshop.global.security.MemberDetails;

class ReviewControllerTests {

    private static final long MEMBER_ID = 7L;
    private static final long ORDER_ITEM_ID = 41L;

    private ReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        MemberDetails memberDetails = new MemberDetails(new MemberAuthenticationView(
                MEMBER_ID, "reviewer@cakeshop.test", "encoded", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        memberDetails, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writableList_authenticatedMember_rendersListWithNavigation() throws Exception {
        when(reviewService.getWritableOrderItems(anyLong(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, null), 0));

        mockMvc.perform(get("/mypage/reviews/writable"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/writable"))
                .andExpect(model().attributeExists("writableItems", "pageNavigation"));
    }

    @Test
    void productReviews_pageParameter_reachesTheServiceAsRequestedPage() throws Exception {
        when(reviewService.getProductReviews(anyLong(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(3, null), 0));

        mockMvc.perform(get("/products/{id}/reviews", 903L).param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/product"))
                .andExpect(model().attributeExists("reviews", "pageNavigation"))
                .andExpect(model().attribute("productId", 903L));

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(reviewService).getProductReviews(eq(903L), pageRequest.capture());
        assertThat(pageRequest.getValue().getPage()).isEqualTo(3);
    }

    @Test
    void myReviews_authenticatedMember_rendersListForTheAuthenticatedMemberOnly()
            throws Exception {

        when(reviewService.getMyReviews(anyLong(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, null), 0));

        mockMvc.perform(get("/mypage/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/my"))
                .andExpect(model().attributeExists("reviews", "pageNavigation"));

        verify(reviewService).getMyReviews(eq(MEMBER_ID), any());
    }

    @Test
    void form_withOrderItemId_rendersFormWithTarget() throws Exception {
        when(reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID)).thenReturn(target());

        mockMvc.perform(get("/reviews/new").param("orderItemId", String.valueOf(ORDER_ITEM_ID)))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/form"))
                .andExpect(model().attributeExists("target", "reviewWriteForm"));
    }

    @Test
    void form_withoutOrderItemId_isBadRequestNotServerError() throws Exception {
        mockMvc.perform(get("/reviews/new"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void form_nonNumericOrderItemId_isBadRequestNotServerError() throws Exception {
        mockMvc.perform(get("/reviews/new").param("orderItemId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void write_validForm_redirectsToWritableList() throws Exception {
        mockMvc.perform(post("/reviews")
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", "5")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews/writable"));

        verify(reviewService).write(any(ReviewWriteForm.class), eq(MEMBER_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "255"})
    void write_ratingOutOfRange_rerendersFormAndSkipsService(String rating) throws Exception {
        when(reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID)).thenReturn(target());

        mockMvc.perform(post("/reviews")
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", rating)
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/form"))
                .andExpect(model().attributeHasFieldErrors("reviewWriteForm", "overallRating"));

        verify(reviewService, never()).write(any(), anyLong());
    }

    @Test
    void write_contentShorterThanTenCharacters_rerendersForm() throws Exception {
        when(reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID)).thenReturn(target());

        mockMvc.perform(post("/reviews")
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", "5")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "  짧다  "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewWriteForm", "content"));

        verify(reviewService, never()).write(any(), anyLong());
    }

    @Test
    void write_missingRating_rerendersForm() throws Exception {
        when(reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID)).thenReturn(target());

        mockMvc.perform(post("/reviews")
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", "")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewWriteForm", "overallRating"));

        verify(reviewService, never()).write(any(), anyLong());
    }

    private OrderReviewTargetView target() {
        return new OrderReviewTargetView(
                ORDER_ITEM_ID,
                903L,
                "딸기 생크림 케이크",
                "ORD-20260810-001",
                true,
                LocalDateTime.of(2026, 8, 1, 10, 0));
    }
}
