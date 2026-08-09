package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

/** 후기 작성 자격 검증과 파생값 계약을 확인한다. */
class ReviewServiceTests {

    private static final long MEMBER_ID = 7L;
    private static final long ORDER_ITEM_ID = 42L;
    /** 계약이 돌려주는 상품 id. 폼이 보내는 값이 아니다. */
    private static final long PRODUCT_ID = 314L;
    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final PageRequest FIRST_PAGE = new PageRequest(1, 20);

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
    void getWritableOrderItems_always_passesWrittenIdsToContract() {
        when(reviewMapper.findReviewedOrderItemIds(MEMBER_ID)).thenReturn(List.of(1L, 2L));
        when(orderReviewQueryService.findWritableOrderItems(anyLong(), any(), any()))
                .thenReturn(new PageResult<>(List.of(itemView()), FIRST_PAGE, 1));

        reviewService.getWritableOrderItems(MEMBER_ID, FIRST_PAGE);

        // 제외를 리뷰가 뒤에서 거르면 건수와 페이지 경계가 어긋난다(A1).
        verify(orderReviewQueryService).findWritableOrderItems(MEMBER_ID, List.of(1L, 2L), FIRST_PAGE);
    }

    @Test
    void getWriteTarget_whenOrderItemBelongsToOtherMember_throwsNotFound() {
        // 계약이 소유자까지 걸러 주므로 남의 것과 없는 것이 같은 빈 결과로 온다.
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);
    }

    @Test
    void getWriteTarget_whenNotPickedUp_throwsNotPickedUp() {
        givenTarget(false);

        assertThatThrownBy(() -> reviewService.getWriteTarget(ORDER_ITEM_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_PICKED_UP);
    }

    @Test
    void write_whenAlreadyReviewed_throwsAlreadyReviewed() {
        givenTarget(true);
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.write(form(), MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED);

        verify(reviewMapper, never()).insert(any());
    }

    @Test
    void write_whenConcurrentInsertViolatesUnique_throwsAlreadyReviewed() {
        givenWritable();
        doThrow(new DuplicateKeyException("uk_reviews_order_item")).when(reviewMapper).insert(any());

        // 중복 검증과 INSERT 사이의 동시 요청은 검증만으로 막히지 않는다(A3).
        assertThatThrownBy(() -> reviewService.write(form(), MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED);
    }

    @Test
    void write_always_derivesProductIdFromOrderItem() {
        givenWritable();
        ReviewWriteForm form = form();

        reviewService.write(form, MEMBER_ID);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(saved.capture());
        // 폼에는 productId 자리가 없다. 요청값을 믿으면 남의 상품에 후기가 붙는다(R4).
        assertThat(saved.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getValue().getMemberId()).isEqualTo(MEMBER_ID);
    }

    private void givenWritable() {
        givenTarget(true);
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(false);
    }

    private void givenTarget(boolean pickedUp) {
        when(orderReviewQueryService.findReviewTarget(ORDER_ITEM_ID, MEMBER_ID))
                .thenReturn(Optional.of(new OrderReviewTargetView(
                        ORDER_ITEM_ID, PRODUCT_ID, "딸기 생크림 케이크", "ORD-20260301-001",
                        pickedUp, PICKED_UP_AT)));
    }

    private static OrderReviewItemView itemView() {
        return new OrderReviewItemView(ORDER_ITEM_ID, "딸기 생크림 케이크", "ORD-20260301-001", PICKED_UP_AT);
    }

    private static ReviewWriteForm form() {
        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(ORDER_ITEM_ID);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("맛있게 잘 먹었습니다. 다음에 또 주문할게요.");
        return form;
    }
}
