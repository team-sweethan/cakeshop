package com.cakeshop.domain.review.service;

import com.cakeshop.domain.order.dto.view.OrderItemDetail;
import com.cakeshop.domain.order.dto.view.PickedUpOrderItem;
import com.cakeshop.domain.order.service.OrderItemQueryService;
import com.cakeshop.domain.product.service.ProductRatingService;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.dto.view.ReviewTargetView;
import com.cakeshop.domain.review.dto.view.WritableReviewView;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성
 * 설명 : 작성 자격 검증과 후기 저장, 상품 평점 재집계를 담당한다. 조각 1(#109) · 2(#33).
 * ******************************
 */
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderItemQueryService orderItemQueryService;
    private final ProductRatingService productRatingService;

    public ReviewService(
            ReviewMapper reviewMapper,
            OrderItemQueryService orderItemQueryService,
            ProductRatingService productRatingService
    ) {
        this.reviewMapper = reviewMapper;
        this.orderItemQueryService = orderItemQueryService;
        this.productRatingService = productRatingService;
    }

    /**
     * 아직 후기를 쓰지 않은 주문 상품을 한 페이지 돌려준다(A1).
     *
     * <p><b>제외 목록을 만들어 주문 계약에 넘긴다.</b> 페이지를 받은 뒤에 리뷰가 거르면
     * 최근 20건이 전부 작성 완료일 때 첫 페이지가 통째로 비고 건수도 틀어진다.
     */
    @Transactional(readOnly = true)
    public PageResult<WritableReviewView> getWritableReviews(
            long memberId, PageRequest pageRequest
    ) {
        List<Long> reviewedOrderItemIds =
                reviewMapper.findReviewedOrderItemIds(memberId);

        PageResult<PickedUpOrderItem> page =
                orderItemQueryService.findPickedUpItems(
                        memberId, reviewedOrderItemIds, pageRequest);

        List<WritableReviewView> content = page.getContent().stream()
                .map(item -> new WritableReviewView(
                        item.orderItemId(),
                        item.productName(),
                        item.orderNumber(),
                        item.pickedUpAt()))
                .toList();

        return new PageResult<>(content, pageRequest, page.getTotalElements());
    }

    /**
     * 후기 작성 폼에 보여 줄 주문 상품을 돌려준다(A2).
     *
     * <p>검증 4가지를 여기서 수행하고, <b>등록(A3)에서 같은 검증을 다시 한다.</b> 폼을 연
     * 시점과 제출 시점 사이가 벌어질 수 있고, 무엇보다 폼 화면을 거치지 않은 직접 호출을
     * 막아야 한다.
     */
    @Transactional(readOnly = true)
    public ReviewTargetView getReviewTarget(long memberId, Long orderItemId) {
        OrderItemDetail orderItem = requireWritableOrderItem(memberId, orderItemId);

        return new ReviewTargetView(
                orderItem.orderItemId(),
                orderItem.productName(),
                orderItem.orderNumber(),
                orderItem.pickedUpAt());
    }

    /**
     * 후기를 저장하고 상품 평점을 다시 집계한다(A3 · D1).
     *
     * <p><b>순서가 정해져 있다: ① 상품 행 잠금 → ② {@code reviews} INSERT → ③ 집계.</b>
     * 셋 다 이 트랜잭션 안이다.
     *
     * <ul>
     *   <li>뒤집으면 교착이다 — INSERT 의 FK 확인이 {@code products} 에 공유 잠금을 먼저 걸고
     *       집계가 배타로 승격하려 한다. 리뷰끼리만이 아니라 재고 차감 흐름과도 부딪힌다.</li>
     *   <li>집계가 같은 트랜잭션이 아니면 후기만 커밋되고 평점이 영구히 어긋난다.</li>
     * </ul>
     *
     * <p>동시 요청이 없으면 결과가 똑같아 <b>단일 스레드 테스트로는 드러나지 않는다.</b>
     */
    @Transactional
    public void createReview(long memberId, ReviewForm form) {
        OrderItemDetail orderItem =
                requireWritableOrderItem(memberId, form.getOrderItemId());

        // ① 집계 대상 상품을 먼저 배타 잠금한다. 저장한 뒤에 잠그면 이미 늦다.
        productRatingService.lockForRating(orderItem.productId());

        // ② productId 는 요청값이 아니라 주문 계약이 돌려준 값이다 (PLAN R4).
        Review review = Review.create(
                orderItem.orderItemId(),
                orderItem.productId(),
                memberId,
                form.getOverallRating(),
                form.getTasteRating(),
                form.getDesignRating(),
                form.getServiceRating(),
                form.getContent());

        try {
            reviewMapper.insertReview(review);
        } catch (DuplicateKeyException e) {
            // 검증과 INSERT 사이의 동시 요청은 검증만으로 막히지 않는다. 커뮤니티 신고 선례.
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        // ③ 같은 트랜잭션에서 재집계한다.
        productRatingService.recalculate(orderItem.productId());
    }

    /**
     * 작성 자격 검증 4가지. 실패하면 즉시 중단한다(SPEC A2).
     *
     * <ol>
     *   <li>주문 상품 존재 — 404</li>
     *   <li>본인 주문 — <b>403 이 아니라 404</b>. 남의 주문 상품 존재를 알려 주지 않는다</li>
     *   <li>픽업 완료 — 400</li>
     *   <li>후기 미작성 — 409</li>
     * </ol>
     */
    private OrderItemDetail requireWritableOrderItem(long memberId, Long orderItemId) {
        if (orderItemId == null) {
            throw new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        OrderItemDetail orderItem = orderItemQueryService.findOrderItem(orderItemId)
                .orElseThrow(() ->
                        new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND));

        if (orderItem.memberId() != memberId) {
            throw new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        if (!orderItem.pickedUp()) {
            throw new BusinessException(ReviewErrorCode.NOT_PICKED_UP);
        }

        if (reviewMapper.existsByOrderItemId(orderItem.orderItemId())) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        return orderItem;
    }
}
