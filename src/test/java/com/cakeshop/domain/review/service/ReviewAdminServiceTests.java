package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.service.MemberReviewQueryService;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.view.AdminReviewDetailView;
import com.cakeshop.domain.review.dto.view.AdminReviewFilter;
import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.dto.view.AdminReviewRating;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewAdminMapper;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

class ReviewAdminServiceTests {

    private static final long REVIEW_ID = 5001L;
    private static final long ORDER_ITEM_ID = 41L;
    private static final long PRODUCT_ID = 903L;
    private static final long MEMBER_ID = 7L;
    private static final LocalDateTime WRITTEN_AT = LocalDateTime.of(2026, 8, 9, 12, 0);

    private ReviewAdminMapper reviewAdminMapper;
    private ReviewMapper reviewMapper;
    private ReviewService reviewService;
    private ProductReviewCommandService productReviewCommandService;
    private MemberReviewQueryService memberReviewQueryService;
    private OrderReviewQueryService orderReviewQueryService;
    private ReviewAdminService reviewAdminService;

    @BeforeEach
    void setUp() {
        reviewAdminMapper = mock(ReviewAdminMapper.class);
        reviewMapper = mock(ReviewMapper.class);
        reviewService = mock(ReviewService.class);
        productReviewCommandService = mock(ProductReviewCommandService.class);
        memberReviewQueryService = mock(MemberReviewQueryService.class);
        orderReviewQueryService = mock(OrderReviewQueryService.class);

        reviewAdminService = new ReviewAdminService(
                reviewAdminMapper,
                reviewMapper,
                reviewService,
                productReviewCommandService,
                memberReviewQueryService,
                orderReviewQueryService);

        when(memberReviewQueryService.getMembersByIds(any())).thenReturn(List.of());
        when(orderReviewQueryService.findOrderItemSnapshots(any())).thenReturn(List.of());
    }

    @Test
    void block_publishedReview_writesWithExpectedStatusAndRecalculates() {
        givenReview(ReviewStatus.PUBLISHED);
        when(reviewAdminMapper.updateStatus(REVIEW_ID, ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED))
                .thenReturn(1);

        reviewAdminService.block(REVIEW_ID);

        verify(reviewAdminMapper)
                .updateStatus(REVIEW_ID, ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED);
        verify(reviewService).recalculateRating(PRODUCT_ID);
    }

    @Test
    void block_locksTheProductBeforeWritingTheReview() {
        givenReview(ReviewStatus.PUBLISHED);
        when(reviewAdminMapper.updateStatus(anyLong(), any(), any())).thenReturn(1);

        reviewAdminService.block(REVIEW_ID);

        InOrder inOrder = inOrder(productReviewCommandService, reviewAdminMapper);
        inOrder.verify(productReviewCommandService).lockForRating(PRODUCT_ID);
        inOrder.verify(reviewAdminMapper).updateStatus(anyLong(), any(), any());
    }

    @Test
    void unblock_blockedReview_publishesAndRecalculates() {
        givenReview(ReviewStatus.BLOCKED);
        when(reviewAdminMapper.updateStatus(REVIEW_ID, ReviewStatus.BLOCKED, ReviewStatus.PUBLISHED))
                .thenReturn(1);

        reviewAdminService.unblock(REVIEW_ID);

        verify(reviewAdminMapper)
                .updateStatus(REVIEW_ID, ReviewStatus.BLOCKED, ReviewStatus.PUBLISHED);
        verify(reviewService).recalculateRating(PRODUCT_ID);
    }

    @Test
    void block_deletedReview_isRejectedWithoutTouchingTheProduct() {
        givenReview(ReviewStatus.DELETED);

        assertThatThrownBy(() -> reviewAdminService.block(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.INVALID_REVIEW_TRANSITION));

        verify(productReviewCommandService, never()).lockForRating(anyLong());
        verify(reviewAdminMapper, never()).updateStatus(anyLong(), any(), any());
        verify(reviewService, never()).recalculateRating(anyLong());
    }

    @Test
    void block_alreadyBlockedReview_isRejected() {
        givenReview(ReviewStatus.BLOCKED);

        assertThatThrownBy(() -> reviewAdminService.block(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.INVALID_REVIEW_TRANSITION));
    }

    @Test
    void unblock_publishedReview_isRejected() {
        givenReview(ReviewStatus.PUBLISHED);

        assertThatThrownBy(() -> reviewAdminService.unblock(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.INVALID_REVIEW_TRANSITION));
    }

    @Test
    void block_missingReview_isNotFound() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(null);

        assertThatThrownBy(() -> reviewAdminService.block(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void block_conditionalWriteMissedAndRowStillThere_isInvalidTransition() {
        givenReview(ReviewStatus.PUBLISHED);
        when(reviewAdminMapper.updateStatus(anyLong(), any(), any())).thenReturn(0);
        when(reviewMapper.findByIdForUpdate(REVIEW_ID))
                .thenReturn(row(ReviewStatus.DELETED));

        assertThatThrownBy(() -> reviewAdminService.block(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.INVALID_REVIEW_TRANSITION));

        verify(reviewService, never()).recalculateRating(anyLong());
    }

    @Test
    void block_conditionalWriteMissedAndRowGone_isNotFound() {
        givenReview(ReviewStatus.PUBLISHED);
        when(reviewAdminMapper.updateStatus(anyLong(), any(), any())).thenReturn(0);
        when(reviewMapper.findByIdForUpdate(REVIEW_ID)).thenReturn(null);

        assertThatThrownBy(() -> reviewAdminService.block(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void getReviews_blankKeywords_leaveTheContractsUncalled() {
        when(reviewAdminMapper.countForAdmin(any())).thenReturn(0L);

        reviewAdminService.getReviews(
                "  ", null, AdminReviewRating.ALL, null, new PageRequest(null, null));

        verify(memberReviewQueryService, never()).findMemberIdsByNickname(any());
        verify(orderReviewQueryService, never()).findOrderItemIdsByProductName(any());
        assertThat(capturedFilter().memberIds()).isNull();
        assertThat(capturedFilter().orderItemIds()).isNull();
    }

    @Test
    void getReviews_keywordMatchingNobody_keepsTheEmptyListSoNothingIsFound() {
        when(memberReviewQueryService.findMemberIdsByNickname("없는사람")).thenReturn(List.of());
        when(reviewAdminMapper.countForAdmin(any())).thenReturn(0L);

        PageResult<AdminReviewListView> result = reviewAdminService.getReviews(
                "없는사람", null, AdminReviewRating.ALL, null, new PageRequest(null, null));

        assertThat(capturedFilter().memberIds())
                .as("빈 목록은 조건 없음이 아니라 '일치하는 회원이 없다'는 답이다")
                .isNotNull()
                .isEmpty();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getReviews_bothKeywords_carryBothIdConditions() {
        when(memberReviewQueryService.findMemberIdsByNickname("홍길동")).thenReturn(List.of(1L, 2L));
        when(orderReviewQueryService.findOrderItemIdsByProductName("케이크"))
                .thenReturn(List.of(11L));
        when(reviewAdminMapper.countForAdmin(any())).thenReturn(0L);

        reviewAdminService.getReviews(
                "홍길동", "케이크", AdminReviewRating.FIVE, ReviewStatus.BLOCKED,
                new PageRequest(null, null));

        AdminReviewFilter filter = capturedFilter();
        assertThat(filter.memberIds()).containsExactly(1L, 2L);
        assertThat(filter.orderItemIds()).containsExactly(11L);
        assertThat(filter.rating()).isEqualTo(AdminReviewRating.FIVE);
        assertThat(filter.status()).isEqualTo(ReviewStatus.BLOCKED);
    }

    @Test
    void getReviews_pageBeyondTheLastOne_skipsTheRowQuery() {
        when(reviewAdminMapper.countForAdmin(any())).thenReturn(3L);

        PageResult<AdminReviewListView> result = reviewAdminService.getReviews(
                null, null, AdminReviewRating.ALL, null, new PageRequest(2, null));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(3L);
        verify(reviewAdminMapper, never()).findForAdmin(any(), anyInt(), anyInt());
    }

    @Test
    void getReviews_withdrawnAuthor_isShownAsWithdrawn() {
        when(reviewAdminMapper.countForAdmin(any())).thenReturn(1L);
        when(reviewAdminMapper.findForAdmin(any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));
        when(memberReviewQueryService.getMembersByIds(any()))
                .thenReturn(List.of(new MemberReviewView(MEMBER_ID, "닉네임", true)));
        when(orderReviewQueryService.findOrderItemSnapshots(any()))
                .thenReturn(List.of(
                        new OrderReviewSnapshotView(ORDER_ITEM_ID, "딸기 케이크", "20260809-0001")));

        PageResult<AdminReviewListView> result = reviewAdminService.getReviews(
                null, null, AdminReviewRating.ALL, null, new PageRequest(null, null));

        assertThat(result.getContent())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.authorName()).isEqualTo("탈퇴한 회원");
                    assertThat(view.productName()).isEqualTo("딸기 케이크");
                });
    }

    @Test
    void getReviewDetail_missingReview_isNotFound() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(null);

        assertThatThrownBy(() -> reviewAdminService.getReviewDetail(REVIEW_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void getReviewDetail_blockedReview_isStillReadable() {
        givenReview(ReviewStatus.BLOCKED);
        when(orderReviewQueryService.findOrderItemSnapshots(any()))
                .thenReturn(List.of(
                        new OrderReviewSnapshotView(ORDER_ITEM_ID, "딸기 케이크", "20260809-0001")));

        AdminReviewDetailView detail = reviewAdminService.getReviewDetail(REVIEW_ID);

        assertThat(detail.isBlocked()).isTrue();
        assertThat(detail.orderNumber()).isEqualTo("20260809-0001");
    }

    private AdminReviewFilter capturedFilter() {
        ArgumentCaptor<AdminReviewFilter> captor = ArgumentCaptor.forClass(AdminReviewFilter.class);
        verify(reviewAdminMapper).countForAdmin(captor.capture());
        return captor.getValue();
    }

    private void givenReview(ReviewStatus status) {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(status));
    }

    private ReviewRow row(ReviewStatus status) {
        return new ReviewRow(
                REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, MEMBER_ID,
                5, 5, 4, 4, "맛있게 잘 먹었습니다.", status, WRITTEN_AT, WRITTEN_AT);
    }
}
