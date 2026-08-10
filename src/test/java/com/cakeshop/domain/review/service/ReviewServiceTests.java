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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.service.MemberReviewQueryService;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

class ReviewServiceTests {

    private static final long MEMBER_ID = 7L;
    private static final long ORDER_ITEM_ID = 41L;
    private static final long PRODUCT_ID = 903L;
    private static final long REVIEW_ID = 5001L;
    private static final LocalDateTime WRITTEN_AT = LocalDateTime.of(2026, 8, 9, 12, 0);

    private ReviewMapper reviewMapper;
    private OrderReviewQueryService orderReviewQueryService;
    private ProductReviewCommandService productReviewCommandService;
    private ProductQueryService productQueryService;
    private MemberReviewQueryService memberReviewQueryService;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ReviewMapper.class);
        orderReviewQueryService = mock(OrderReviewQueryService.class);
        productReviewCommandService = mock(ProductReviewCommandService.class);
        productQueryService = mock(ProductQueryService.class);
        memberReviewQueryService = mock(MemberReviewQueryService.class);
        reviewService = new ReviewService(
                reviewMapper,
                orderReviewQueryService,
                productReviewCommandService,
                productQueryService,
                memberReviewQueryService);
    }

    @Test
    void getProductReviews_productNotOnSale_throwsReviewNotFoundNotNotOnSale() {
        when(productQueryService.getSalesInfo(PRODUCT_ID))
                .thenThrow(new BusinessException(ProductErrorCode.NOT_ON_SALE));

        assertThatThrownBy(() -> reviewService.getProductReviews(PRODUCT_ID, new PageRequest(1, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));

        verify(reviewMapper, never()).countPublishedByProductId(anyLong());
    }

    @Test
    void getProductReviews_missingProduct_throwsTheSameReviewNotFound() {
        when(productQueryService.getSalesInfo(PRODUCT_ID))
                .thenThrow(new BusinessException(ProductErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> reviewService.getProductReviews(PRODUCT_ID, new PageRequest(1, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void getProductReviews_withdrawnAuthor_isMaskedButReviewRemains() {
        when(reviewMapper.countPublishedByProductId(PRODUCT_ID)).thenReturn(1L);
        when(reviewMapper.findPublishedByProductId(PRODUCT_ID, 0, PageRequest.DEFAULT_SIZE))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));
        when(memberReviewQueryService.getMembersByIds(List.of(MEMBER_ID)))
                .thenReturn(List.of(new MemberReviewView(MEMBER_ID, "떠난사람", true)));

        PageResult<ProductReviewView> reviews =
                reviewService.getProductReviews(PRODUCT_ID, new PageRequest(1, null));

        assertThat(reviews.getContent()).singleElement().satisfies(review -> {
            assertThat(review.authorName()).isEqualTo(ProductReviewView.WITHDRAWN_AUTHOR_NAME);
            assertThat(review.content()).isEqualTo("맛있게 잘 먹었습니다.");
        });
    }

    @Test
    void getProductReviews_authorMissingFromContract_stillRendersTheReview() {
        when(reviewMapper.countPublishedByProductId(PRODUCT_ID)).thenReturn(1L);
        when(reviewMapper.findPublishedByProductId(PRODUCT_ID, 0, PageRequest.DEFAULT_SIZE))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));
        when(memberReviewQueryService.getMembersByIds(List.of(MEMBER_ID))).thenReturn(List.of());

        PageResult<ProductReviewView> reviews =
                reviewService.getProductReviews(PRODUCT_ID, new PageRequest(1, null));

        assertThat(reviews.getContent()).singleElement()
                .extracting(ProductReviewView::authorName)
                .isEqualTo(ProductReviewView.WITHDRAWN_AUTHOR_NAME);
    }

    @Test
    void getProductReviews_pageBeyondLastPage_skipsTheListQuery() {
        when(reviewMapper.countPublishedByProductId(PRODUCT_ID)).thenReturn(2L);

        PageResult<ProductReviewView> reviews =
                reviewService.getProductReviews(PRODUCT_ID, new PageRequest(2, null));

        assertThat(reviews.getContent()).isEmpty();
        assertThat(reviews.getTotalElements()).isEqualTo(2L);
        verify(reviewMapper, never()).findPublishedByProductId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void getProductReviewPreview_takesLatestThreeWithoutCheckingTheProductAgain() {
        when(reviewMapper.findPublishedByProductId(PRODUCT_ID, 0, 3))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));

        assertThat(reviewService.getProductReviewPreview(PRODUCT_ID)).hasSize(1);

        verify(reviewMapper).findPublishedByProductId(PRODUCT_ID, 0, 3);
        verify(productQueryService, never()).getSalesInfo(anyLong());
    }

    @Test
    void getMyReviews_blockedReview_isShownToTheAuthorWithTheProductSnapshot() {
        when(reviewMapper.countByMemberId(MEMBER_ID)).thenReturn(1L);
        when(reviewMapper.findByMemberId(MEMBER_ID, 0, PageRequest.DEFAULT_SIZE))
                .thenReturn(List.of(row(ReviewStatus.BLOCKED)));
        when(orderReviewQueryService.findOrderItemSnapshots(List.of(ORDER_ITEM_ID)))
                .thenReturn(List.of(new OrderReviewSnapshotView(
                        ORDER_ITEM_ID, "딸기 생크림 케이크", "ORD-0001")));

        PageResult<MyReviewView> reviews =
                reviewService.getMyReviews(MEMBER_ID, new PageRequest(1, null));

        assertThat(reviews.getContent()).singleElement().satisfies(review -> {
            assertThat(review.isBlocked()).isTrue();
            assertThat(review.productId()).isEqualTo(PRODUCT_ID);
            assertThat(review.productName()).isEqualTo("딸기 생크림 케이크");
            assertThat(review.orderNumber()).isEqualTo("ORD-0001");
        });
    }

    @Test
    void getMyReviews_snapshotMissing_stillRendersTheReview() {
        when(reviewMapper.countByMemberId(MEMBER_ID)).thenReturn(1L);
        when(reviewMapper.findByMemberId(MEMBER_ID, 0, PageRequest.DEFAULT_SIZE))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));
        when(orderReviewQueryService.findOrderItemSnapshots(List.of(ORDER_ITEM_ID)))
                .thenReturn(List.of());

        PageResult<MyReviewView> reviews =
                reviewService.getMyReviews(MEMBER_ID, new PageRequest(1, null));

        assertThat(reviews.getContent()).singleElement().satisfies(review -> {
            assertThat(review.productName()).isNull();
            assertThat(review.productId())
                    .as("상품 링크는 후기가 가진 값이라 스냅샷이 비어도 살아 있어야 한다")
                    .isEqualTo(PRODUCT_ID);
            assertThat(review.content()).isEqualTo("맛있게 잘 먹었습니다.");
        });
    }

    @Test
    void getMyReviews_neverAsksTheMemberContractForItsOwnAuthor() {
        when(reviewMapper.countByMemberId(MEMBER_ID)).thenReturn(1L);
        when(reviewMapper.findByMemberId(MEMBER_ID, 0, PageRequest.DEFAULT_SIZE))
                .thenReturn(List.of(row(ReviewStatus.PUBLISHED)));

        reviewService.getMyReviews(MEMBER_ID, new PageRequest(1, null));

        verify(memberReviewQueryService, never()).getMembersByIds(any());
    }

    private ReviewRow row(ReviewStatus status) {
        return rowOf(MEMBER_ID, status);
    }

    @Test
    void getWritableOrderItems_reviewedItems_areExcludedByContractNotByCaller() {
        List<Long> reviewed = List.of(11L, 12L);
        PageRequest pageRequest = new PageRequest(1, null);
        when(reviewMapper.findReviewedOrderItemIds(MEMBER_ID)).thenReturn(reviewed);
        when(orderReviewQueryService.findWritableOrderItems(anyLong(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), pageRequest, 0));

        reviewService.getWritableOrderItems(MEMBER_ID, pageRequest);

        verify(orderReviewQueryService)
                .findWritableOrderItems(MEMBER_ID, reviewed, pageRequest);
    }

    @Test
    void getWriteTarget_orderItemMissingOrNotOwned_throwsOrderItemNotFound() {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.ORDER_ITEM_NOT_FOUND));
    }

    @Test
    void getWriteTarget_notPickedUp_throwsNotPickedUpNotOrderItemNotFound() {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.of(target(false)));

        assertThatThrownBy(() -> reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.NOT_PICKED_UP));
    }

    @Test
    void write_alreadyReviewedOrderItem_throwsAlreadyReviewedAndSkipsInsert() {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.of(target(true)));
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.write(form(), MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED));

        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void write_duplicateKeyFromConcurrentInsert_throwsAlreadyReviewed() {
        givenWritableTarget();
        when(reviewMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("uk_reviews_order_item"));

        assertThatThrownBy(() -> reviewService.write(form(), MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED));
    }

    @Test
    void write_savedReview_takesProductIdFromContractAndMemberIdFromCaller() {
        givenWritableTarget();

        reviewService.write(form(), MEMBER_ID);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getValue().getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
    }

    @Test
    void write_formAlreadyValidatedOnOpen_revalidatesEligibilityAgain() {
        givenWritableTarget();

        reviewService.write(form(), MEMBER_ID);

        verify(orderReviewQueryService).findReviewTarget(ORDER_ITEM_ID, MEMBER_ID);
        verify(reviewMapper).existsByOrderItemId(ORDER_ITEM_ID);
    }

    @Test
    void write_ratingAggregate_isAppliedAfterLockAndInsertInThatOrder() {
        givenWritableTarget();

        reviewService.write(form(), MEMBER_ID);

        InOrder order = inOrder(productReviewCommandService, reviewMapper);
        order.verify(productReviewCommandService).lockForRating(PRODUCT_ID);
        order.verify(reviewMapper).insert(any());
        order.verify(reviewMapper).aggregateForUpdate(PRODUCT_ID);
        order.verify(productReviewCommandService)
                .applyReviewAggregate(PRODUCT_ID, new BigDecimal("4.50"), 2L);
    }

    @Test
    void write_duplicateKey_skipsAggregateSoProductKeepsItsValue() {
        givenWritableTarget();
        when(reviewMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("uk_reviews_order_item"));

        assertThatThrownBy(() -> reviewService.write(form(), MEMBER_ID))
                .isInstanceOf(BusinessException.class);

        verify(productReviewCommandService, never())
                .applyReviewAggregate(anyLong(), any(), anyLong());
    }

    @Test
    void getEditableReview_otherMembersReview_throwsReviewNotFoundNotForbidden() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(rowOf(999L, ReviewStatus.PUBLISHED));

        assertThatThrownBy(() -> reviewService.getEditableReview(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void getEditableReview_deletedReview_throwsTheSameReviewNotFound() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.DELETED));

        assertThatThrownBy(() -> reviewService.getEditableReview(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void getEditableReview_blockedReview_throwsBlockedReviewSoTheAuthorLearnsWhy() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.BLOCKED));

        assertThatThrownBy(() -> reviewService.getEditableReview(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.BLOCKED_REVIEW));
    }

    @Test
    void edit_savedReview_keepsOrderItemAndProductOutOfTheUpdate() {
        givenEditableReview();

        reviewService.edit(REVIEW_ID, editForm(3), MEMBER_ID);

        ArgumentCaptor<Review> updated = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).update(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(REVIEW_ID);
        assertThat(updated.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(updated.getValue().getOverallRating()).isEqualTo(3);
        assertThat(updated.getValue().getOrderItemId()).isNull();
        assertThat(updated.getValue().getProductId()).isNull();
    }

    @Test
    void edit_overallRatingUnchanged_stillRecalculatesTheAggregate() {
        givenEditableReview();

        reviewService.edit(REVIEW_ID, editForm(5), MEMBER_ID);

        verify(reviewMapper).aggregateForUpdate(PRODUCT_ID);
        verify(productReviewCommandService)
                .applyReviewAggregate(PRODUCT_ID, new BigDecimal("4.50"), 2L);
    }

    @Test
    void edit_ratingAggregate_isAppliedAfterLockAndUpdateInThatOrder() {
        givenEditableReview();

        reviewService.edit(REVIEW_ID, editForm(3), MEMBER_ID);

        InOrder order = inOrder(productReviewCommandService, reviewMapper);
        order.verify(productReviewCommandService).lockForRating(PRODUCT_ID);
        order.verify(reviewMapper).update(any());
        order.verify(reviewMapper).aggregateForUpdate(PRODUCT_ID);
        order.verify(productReviewCommandService)
                .applyReviewAggregate(PRODUCT_ID, new BigDecimal("4.50"), 2L);
    }

    @Test
    void edit_blockedBetweenCheckAndUpdate_throwsBlockedReviewAndSkipsAggregate() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.PUBLISHED));
        when(reviewMapper.findByIdForUpdate(REVIEW_ID)).thenReturn(row(ReviewStatus.BLOCKED));
        when(reviewMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> reviewService.edit(REVIEW_ID, editForm(3), MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.BLOCKED_REVIEW));

        verify(productReviewCommandService, never())
                .applyReviewAggregate(anyLong(), any(), anyLong());
    }

    @Test
    void edit_updateAppliedToNoRowWithNoVisibleCause_throwsInvalidTransition() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.PUBLISHED));
        when(reviewMapper.findByIdForUpdate(REVIEW_ID)).thenReturn(row(ReviewStatus.PUBLISHED));
        when(reviewMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> reviewService.edit(REVIEW_ID, editForm(3), MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.INVALID_REVIEW_TRANSITION));
    }

    @Test
    void edit_blockedReview_isRejectedBeforeTheProductIsLocked() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.BLOCKED));

        assertThatThrownBy(() -> reviewService.edit(REVIEW_ID, editForm(3), MEMBER_ID))
                .isInstanceOf(BusinessException.class);

        verify(productReviewCommandService, never()).lockForRating(anyLong());
        verify(reviewMapper, never()).update(any());
    }

    @Test
    void delete_publishedReview_softDeletesAndRecalculatesInThatOrder() {
        givenEditableReview();
        when(reviewMapper.deleteByAuthor(REVIEW_ID, MEMBER_ID)).thenReturn(1);

        reviewService.delete(REVIEW_ID, MEMBER_ID);

        InOrder order = inOrder(productReviewCommandService, reviewMapper);
        order.verify(productReviewCommandService).lockForRating(PRODUCT_ID);
        order.verify(reviewMapper).deleteByAuthor(REVIEW_ID, MEMBER_ID);
        order.verify(productReviewCommandService)
                .applyReviewAggregate(PRODUCT_ID, new BigDecimal("4.50"), 2L);
    }

    @Test
    void delete_blockedReview_throwsBlockedReviewAndTouchesNothing() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.BLOCKED));

        assertThatThrownBy(() -> reviewService.delete(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.BLOCKED_REVIEW));

        verify(reviewMapper, never()).deleteByAuthor(anyLong(), anyLong());
        verify(productReviewCommandService, never())
                .applyReviewAggregate(anyLong(), any(), anyLong());
    }

    @Test
    void delete_otherMembersReview_throwsReviewNotFoundAndTouchesNothing() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(rowOf(999L, ReviewStatus.PUBLISHED));

        assertThatThrownBy(() -> reviewService.delete(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));

        verify(reviewMapper, never()).deleteByAuthor(anyLong(), anyLong());
    }

    @Test
    void delete_deletedBetweenCheckAndUpdate_throwsReviewNotFoundAndSkipsAggregate() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.PUBLISHED));
        when(reviewMapper.findByIdForUpdate(REVIEW_ID)).thenReturn(row(ReviewStatus.DELETED));
        when(reviewMapper.deleteByAuthor(REVIEW_ID, MEMBER_ID)).thenReturn(0);

        assertThatThrownBy(() -> reviewService.delete(REVIEW_ID, MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));

        verify(productReviewCommandService, never())
                .applyReviewAggregate(anyLong(), any(), anyLong());
    }

    private void givenEditableReview() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(row(ReviewStatus.PUBLISHED));
        when(reviewMapper.update(any())).thenReturn(1);
        when(reviewMapper.aggregateForUpdate(PRODUCT_ID))
                .thenReturn(new ProductRatingAggregate(new BigDecimal("4.50"), 2L));
    }

    private ReviewEditForm editForm(int overallRating) {
        ReviewEditForm form = new ReviewEditForm();
        form.setOverallRating(overallRating);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("다시 먹어 보고 평점을 고쳤습니다.");
        return form;
    }

    private ReviewRow rowOf(long memberId, ReviewStatus status) {
        return new ReviewRow(
                REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, memberId,
                5, 5, 4, 4, "맛있게 잘 먹었습니다.", status, WRITTEN_AT);
    }

    private void givenWritableTarget() {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.of(target(true)));
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(false);
        when(reviewMapper.aggregateForUpdate(PRODUCT_ID))
                .thenReturn(new ProductRatingAggregate(new BigDecimal("4.50"), 2L));
    }

    private OrderReviewTargetView target(boolean pickedUp) {
        return new OrderReviewTargetView(
                ORDER_ITEM_ID,
                PRODUCT_ID,
                "딸기 생크림 케이크",
                "ORD-20260810-001",
                pickedUp,
                pickedUp ? LocalDateTime.of(2026, 8, 1, 10, 0) : null);
    }

    private ReviewWriteForm form() {
        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(ORDER_ITEM_ID);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("맛있게 잘 먹었습니다. 다음에도 주문할게요.");
        return form;
    }
}
