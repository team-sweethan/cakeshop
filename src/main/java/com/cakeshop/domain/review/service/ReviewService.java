package com.cakeshop.domain.review.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.ProductRatingAggregate;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderReviewQueryService orderReviewQueryService;
    private final ProductReviewCommandService productReviewCommandService;

    public ReviewService(
            ReviewMapper reviewMapper,
            OrderReviewQueryService orderReviewQueryService,
            ProductReviewCommandService productReviewCommandService) {
        this.reviewMapper = reviewMapper;
        this.orderReviewQueryService = orderReviewQueryService;
        this.productReviewCommandService = productReviewCommandService;
    }

    @Transactional(readOnly = true)
    public PageResult<OrderReviewItemView> getWritableOrderItems(
            long memberId, PageRequest pageRequest) {

        // 이미 쓴 것을 계약에 넘겨 SQL 안에서 거르게 한다. 목록을 받아 뒤에서 걸러 내면 최신
        // 20건이 모두 작성 완료일 때 첫 페이지가 통째로 비고 전체 건수도 어긋난다 (A1).
        List<Long> reviewedOrderItemIds = reviewMapper.findReviewedOrderItemIds(memberId);

        return orderReviewQueryService.findWritableOrderItems(
                memberId, reviewedOrderItemIds, pageRequest);
    }

    @Transactional(readOnly = true)
    public OrderReviewTargetView getWriteTarget(long orderItemId, long memberId) {
        return requireWritableTarget(orderItemId, memberId);
    }

    @Transactional
    public void write(ReviewWriteForm form, long memberId) {
        // 폼을 연 뒤 제출까지 시간이 벌어질 수 있고, 폼을 거치지 않은 직접 호출도 막아야 한다.
        OrderReviewTargetView target = requireWritableTarget(form.getOrderItemId(), memberId);

        // 저장보다 먼저 잠근다. 뒤집으면 INSERT 의 FK 확인이 상품 행에 공유 잠금을 걸고 집계가
        // 그것을 배타로 승격하려 해, 같은 상품에 후기가 동시에 들어올 때 교착한다 (D1).
        productReviewCommandService.lockForRating(target.productId());

        Review review = Review.create(
                target.orderItemId(),
                target.productId(),   // 요청값이 아니라 계약이 준 상품 식별자다 (R4).
                memberId,
                form.getOverallRating(),
                form.getTasteRating(),
                form.getDesignRating(),
                form.getServiceRating(),
                form.getContent());

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 아래 4번 검증과 INSERT 사이에 다른 요청이 먼저 저장할 수 있다. 검증만으로는
            // 막히지 않고 uk_reviews_order_item 이 최종 방어선이다.
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        recalculateRating(target.productId());
    }

    // 후기 쓰기와 같은 트랜잭션이어야 한다. 후기만 커밋되고 집계가 실패하면 그 상품에 다음 쓰기가
    // 올 때까지 아무도 모르는 채 틀린 평점과 정렬이 나간다 (D1).
    private void recalculateRating(long productId) {
        ProductRatingAggregate aggregate = reviewMapper.aggregateForUpdate(productId);

        productReviewCommandService.applyReviewAggregate(
                productId, aggregate.averageRating(), aggregate.reviewCount());
    }

    private OrderReviewTargetView requireWritableTarget(Long orderItemId, long memberId) {
        if (orderItemId == null) {
            throw new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // 1·2 — 없는 것과 남의 것을 가려 주지 않는다. 403 으로 나누면 남의 주문 상품 식별자를
        // 훑어 존재 여부를 확인할 수 있다 (DOMAIN 2.5).
        OrderReviewTargetView target = orderReviewQueryService
                .findReviewTarget(orderItemId, memberId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND));

        // 3 — 픽업 전
        if (!target.pickedUp()) {
            throw new BusinessException(ReviewErrorCode.NOT_PICKED_UP);
        }

        // 4 — 중복
        if (reviewMapper.existsByOrderItemId(target.orderItemId())) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        return target;
    }
}
