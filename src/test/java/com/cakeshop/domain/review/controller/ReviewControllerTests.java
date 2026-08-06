package com.cakeshop.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.review.dto.view.ReviewTargetView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.error.GlobalExceptionHandler;
import com.cakeshop.global.security.MemberDetails;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code orderItemId} 가 없거나 숫자가 아닌 요청이 <b>400</b> 인지 확인한다(SPEC A2).
 *
 * <p>둘 다 틀린 요청이지 못 찾은 것이 아니다. 없는 것을 404 로 돌려주면 "그런 주문 상품이
 * 없다"로 읽히고, 숫자가 아닌 값은 그냥 두면 변환 예외가 최후의 방어선까지 떨어져
 * <b>500 에 스택트레이스</b>가 된다. 서버가 고장 난 것처럼 보인다.
 */
class ReviewControllerTests {

    private ReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewController(reviewService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MemberDetails member = new MemberDetails(new MemberAuthenticationView(
                42L, "user@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        member, null, member.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reviewForm_missingOrderItemId_isBadRequest() throws Exception {
        mockMvc.perform(get("/reviews/new"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewForm_nonNumericOrderItemId_isBadRequest() throws Exception {
        mockMvc.perform(get("/reviews/new").param("orderItemId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewForm_blankOrderItemId_isBadRequest() throws Exception {
        mockMvc.perform(get("/reviews/new").param("orderItemId", ""))
                .andExpect(status().isBadRequest());
    }

    /** 제대로 온 요청까지 400 이 되면 안 된다 — 위 셋의 반대쪽을 같이 고정한다. */
    @Test
    void reviewForm_validOrderItemId_rendersForm() throws Exception {
        when(reviewService.getReviewTarget(anyLong(), any()))
                .thenReturn(new ReviewTargetView(
                        7L, "딸기 케이크", "ORD-001",
                        LocalDateTime.of(2026, 8, 1, 12, 0)));

        mockMvc.perform(get("/reviews/new").param("orderItemId", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/form"));
    }
}
