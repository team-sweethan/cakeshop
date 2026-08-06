package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.dto.view.OrderItemDetail;
import com.cakeshop.domain.order.dto.view.PickedUpOrderItem;
import com.cakeshop.domain.order.service.OrderItemQueryService;
import com.cakeshop.domain.product.service.ProductRatingService;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

/**
 * 후기 작성의 규칙을 고정한다(docs/review/SPEC.md A1·A2·A3, D1).
 *
 * <p>여기서 잡는 것들은 <b>화면으로는 정상으로 보인다.</b> 남의 주문에 후기를 써도 저장은
 * 성공하고, 집계를 잘못된 순서로 불러도 요청이 하나뿐이면 결과가 똑같다.
 */
class ReviewServiceTests {

    private static final long MEMBER_ID = 7L;
    private static final long OTHER_MEMBER_ID = 8L;
    private static final long ORDER_ITEM_ID = 42L;
    private static final long PRODUCT_ID = 100L;
    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    private ReviewMapper reviewMapper;
    private OrderItemQueryService orderItemQueryService;
    private ProductRatingService productRatingService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ReviewMapper.class);
        orderItemQueryService = mock(OrderItemQueryService.class);
        productRatingService = mock(ProductRatingService.class);

        reviewService = new ReviewService(
                reviewMapper, orderItemQueryService, productRatingService);
    }

    /**
     * 잠금 → INSERT → 집계 순서를 고정한다.
     *
     * <p>세 목을 <b>함께</b> {@code inOrder}에 넣는다. 한쪽만 넣으면 다른 쪽 호출이 순서
     * 판단에서 통째로 빠져서, 저장한 뒤에 잠그는 구현이 그대로 통과한다.
     *
     * <p>순서를 뒤집으면 {@code reviews} INSERT의 FK 확인이 {@code products}에 공유 잠금을
     * 먼저 걸고 집계가 배타로 승격하려 해서 교착이다. <b>단일 요청으로는 결과가 똑같아
     * 드러나지 않는다.</b>
     */
    @Test
    void createReview_locksProductBeforeInsertThenRecalculates() {
        givenWritableOrderItem();

        reviewService.createReview(MEMBER_ID, form());

        InOrder inOrder = inOrder(productRatingService, reviewMapper);
        inOrder.verify(productRatingService).lockForRating(PRODUCT_ID);
        inOrder.verify(reviewMapper).insertReview(any(Review.class));
        inOrder.verify(productRatingService).recalculate(PRODUCT_ID);
    }

    /**
     * {@code product_id}는 요청값이 아니라 주문 상품에서 파생시킨다(PLAN R4).
     *
     * <p>폼에 {@code productId}가 없으므로 컴파일로도 막히지만, 나중에 편의로 필드를 열면
     * 남의 상품에 후기를 붙일 수 있다. 파생 경로를 테스트로 고정한다.
     */
    @Test
    void createReview_derivesProductIdFromOrderItem() {
        givenWritableOrderItem();

        reviewService.createReview(MEMBER_ID, form());

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insertReview(captor.capture());

        assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getMemberId()).isEqualTo(MEMBER_ID);
    }

    /** 남의 주문 상품은 403이 아니라 404다. 존재 자체를 알려 주지 않는다(SPEC 2.5). */
    @Test
    void createReview_otherMembersOrderItem_isNotFoundNotForbidden() {
        when(orderItemQueryService.findOrderItem(ORDER_ITEM_ID))
                .thenReturn(Optional.of(orderItem(OTHER_MEMBER_ID, true)));

        assertThatThrownBy(() -> reviewService.createReview(MEMBER_ID, form()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);

        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createReview_orderItemMissing_isNotFound() {
        when(orderItemQueryService.findOrderItem(ORDER_ITEM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(MEMBER_ID, form()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);
    }

    @Test
    void createReview_notPickedUp_isRejected() {
        when(orderItemQueryService.findOrderItem(ORDER_ITEM_ID))
                .thenReturn(Optional.of(orderItem(MEMBER_ID, false)));

        assertThatThrownBy(() -> reviewService.createReview(MEMBER_ID, form()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_PICKED_UP);

        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createReview_alreadyReviewed_isRejected() {
        when(orderItemQueryService.findOrderItem(ORDER_ITEM_ID))
                .thenReturn(Optional.of(orderItem(MEMBER_ID, true)));
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(MEMBER_ID, form()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED);

        verify(reviewMapper, never()).insertReview(any());
    }

    /**
     * 검증과 INSERT 사이의 동시 요청은 검증만으로 막히지 않는다.
     *
     * <p>{@code uk_reviews_order_item}이 실제로 막고, 그 예외를 도메인 오류로 바꾼다.
     * 커뮤니티 신고 선례와 같다.
     */
    @Test
    void createReview_duplicateKeyOnInsert_becomesAlreadyReviewed() {
        givenWritableOrderItem();
        when(reviewMapper.insertReview(any(Review.class)))
                .thenThrow(new DuplicateKeyException("uk_reviews_order_item"));

        assertThatThrownBy(() -> reviewService.createReview(MEMBER_ID, form()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_REVIEWED);

        verify(productRatingService, never()).recalculate(anyLong());
    }

    /**
     * 이미 쓴 항목을 <b>계약에 넘겨</b> 거른다. 받아 온 뒤에 리뷰가 거르면 안 된다.
     *
     * <p>뒤에서 거르면 최근 20건이 전부 작성 완료일 때 첫 페이지가 통째로 비고, 그 항목들이
     * 다음 페이지로 밀리며 전체 건수도 틀어진다. <b>목록이 비는 것은 정상 상태라 오류로도
     * 드러나지 않는다.</b>
     */
    @Test
    void getWritableReviews_passesReviewedIdsToOrderContract() {
        List<Long> reviewed = List.of(1L, 2L, 3L);
        PageRequest pageRequest = new PageRequest(1, 20);

        when(reviewMapper.findReviewedOrderItemIds(MEMBER_ID)).thenReturn(reviewed);
        when(orderItemQueryService.findPickedUpItems(
                eq(MEMBER_ID), eq(reviewed), eq(pageRequest)))
                .thenReturn(new PageResult<>(
                        List.of(new PickedUpOrderItem(
                                ORDER_ITEM_ID, "딸기 케이크", "ORD-1", PICKED_UP_AT)),
                        pageRequest, 1));

        var result = reviewService.getWritableReviews(MEMBER_ID, pageRequest);

        verify(orderItemQueryService).findPickedUpItems(MEMBER_ID, reviewed, pageRequest);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).orderItemId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private void givenWritableOrderItem() {
        when(orderItemQueryService.findOrderItem(ORDER_ITEM_ID))
                .thenReturn(Optional.of(orderItem(MEMBER_ID, true)));
        when(reviewMapper.existsByOrderItemId(ORDER_ITEM_ID)).thenReturn(false);
    }

    private OrderItemDetail orderItem(long memberId, boolean pickedUp) {
        return new OrderItemDetail(
                ORDER_ITEM_ID, memberId, PRODUCT_ID, pickedUp,
                "딸기 생크림 케이크", "ORD-1", pickedUp ? PICKED_UP_AT : null);
    }

    private ReviewForm form() {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(ORDER_ITEM_ID);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(5);
        form.setContent("정말 맛있게 잘 먹었습니다. 다음에 또 주문할게요.");
        return form;
    }
}
