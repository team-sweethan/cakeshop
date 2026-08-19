package com.cakeshop.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.GlobalExceptionHandler;
import com.cakeshop.global.security.MemberDetails;

class ReviewControllerTests {

    private static final long MEMBER_ID = 7L;
    private static final long ORDER_ITEM_ID = 41L;
    private static final long REVIEW_ID = 5001L;

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
    void myFocusedReviews_notificationDeepLink_rendersTheListAroundTheTargetReview()
            throws Exception {

        when(reviewService.getFocusedMyReviews(anyLong(), anyLong()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, null), 0));

        mockMvc.perform(get("/mypage/reviews/{reviewId}", REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/my"))
                .andExpect(model().attributeExists("reviews", "pageNavigation"));

        verify(reviewService).getFocusedMyReviews(MEMBER_ID, REVIEW_ID);
        verify(reviewService, never()).getMyReviews(anyLong(), any());
    }

    @Test
    void myFocusedReviews_writableListPath_isNotSwallowedByTheDeepLinkMapping()
            throws Exception {

        when(reviewService.getWritableOrderItems(anyLong(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, null), 0));

        mockMvc.perform(get("/mypage/reviews/writable"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/writable"));

        verify(reviewService, never()).getFocusedMyReviews(anyLong(), anyLong());
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
    void write_validForm_redirectsToMyReviewsSoTheAuthorSeesWhatWasSaved() throws Exception {
        mockMvc.perform(post("/reviews")
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", "5")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews"));

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

    @Test
    void write_imageLimitError_rerendersTheFormWithAnImageFieldError() throws Exception {
        when(reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID)).thenReturn(target());
        doThrow(new BusinessException(ReviewErrorCode.IMAGE_LIMIT_EXCEEDED))
                .when(reviewService).write(any(), eq(MEMBER_ID));

        mockMvc.perform(multipart("/reviews")
                        .file(new MockMultipartFile(
                                "images", "review.jpg", "image/jpeg",
                                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                        .param("orderItemId", String.valueOf(ORDER_ITEM_ID))
                        .param("overallRating", "5")
                        .param("tasteRating", "5")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "맛있게 잘 먹었습니다. 다음에도 주문할게요."))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/form"))
                .andExpect(model().attributeHasFieldErrors("reviewWriteForm", "images"));
    }

    @Test
    void editForm_ownReview_prefillsTheFormWithWhatWasSaved() throws Exception {
        when(reviewService.getEditableReview(REVIEW_ID, MEMBER_ID)).thenReturn(myReview());

        mockMvc.perform(get("/reviews/{id}/edit", REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/edit"))
                .andExpect(model().attributeExists("review"))
                .andExpect(model().attribute(
                        "reviewEditForm",
                        hasProperty("overallRating", is(5))))
                .andExpect(model().attribute(
                        "reviewEditForm",
                        hasProperty(
                                "content", is("맛있게 잘 먹었습니다."))));
    }

    @Test
    void edit_validForm_redirectsToMyReviews() throws Exception {
        mockMvc.perform(post("/reviews/{id}/edit", REVIEW_ID)
                        .param("overallRating", "3")
                        .param("tasteRating", "3")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "다시 먹어 보고 평점을 고쳤습니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews"));

        verify(reviewService).edit(eq(REVIEW_ID), any(ReviewEditForm.class), eq(MEMBER_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "255"})
    void edit_ratingOutOfRange_rerendersFormAndSkipsService(String rating) throws Exception {
        when(reviewService.getEditableReview(REVIEW_ID, MEMBER_ID)).thenReturn(myReview());

        mockMvc.perform(post("/reviews/{id}/edit", REVIEW_ID)
                        .param("overallRating", rating)
                        .param("tasteRating", "3")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "다시 먹어 보고 평점을 고쳤습니다."))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/review/edit"))
                .andExpect(model().attributeHasFieldErrors("reviewEditForm", "overallRating"));

        verify(reviewService, never()).edit(anyLong(), any(), anyLong());
    }

    @Test
    void edit_rerenderedForm_keepsWhatWasTypedNotWhatWasSaved() throws Exception {
        when(reviewService.getEditableReview(REVIEW_ID, MEMBER_ID)).thenReturn(myReview());

        mockMvc.perform(post("/reviews/{id}/edit", REVIEW_ID)
                        .param("overallRating", "3")
                        .param("tasteRating", "3")
                        .param("designRating", "4")
                        .param("serviceRating", "4")
                        .param("content", "짧다"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("reviewEditForm", "content"))
                .andExpect(model().attribute(
                        "reviewEditForm",
                        hasProperty("overallRating", is(3))));
    }

    @Test
    void delete_ownReview_redirectsToMyReviews() throws Exception {
        mockMvc.perform(post("/reviews/{id}/delete", REVIEW_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/reviews"));

        verify(reviewService).delete(REVIEW_ID, MEMBER_ID);
    }

    private MyReviewView myReview() {
        return new MyReviewView(
                REVIEW_ID,
                903L,
                "딸기 생크림 케이크",
                "ORD-20260810-001",
                5, 5, 4, 4,
                "맛있게 잘 먹었습니다.",
                LocalDateTime.of(2026, 8, 9, 12, 0),
                ReviewStatus.PUBLISHED,
                List.of(),
                null);
    }

    private OrderReviewTargetView target() {
        return new OrderReviewTargetView(
                ORDER_ITEM_ID,
                903L,
                "딸기 생크림 케이크",
                "ORD-20260810-001",
                true);
    }
}
