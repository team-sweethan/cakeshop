package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

class ReviewServiceTests {

    private static final long MEMBER_ID = 7L;
    private static final long ORDER_ITEM_ID = 41L;
    private static final long PRODUCT_ID = 903L;

    private ReviewMapper reviewMapper;
    private OrderReviewQueryService orderReviewQueryService;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ReviewMapper.class);
        orderReviewQueryService = mock(OrderReviewQueryService.class);
        reviewService = new ReviewService(reviewMapper, orderReviewQueryService);
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

    private void givenWritableTarget() {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.of(target(true)));
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(false);
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
