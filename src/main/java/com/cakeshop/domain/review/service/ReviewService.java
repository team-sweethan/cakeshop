package com.cakeshop.domain.review.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderReviewQueryService orderReviewQueryService;

    public ReviewService(
            ReviewMapper reviewMapper, OrderReviewQueryService orderReviewQueryService) {
        this.reviewMapper = reviewMapper;
        this.orderReviewQueryService = orderReviewQueryService;
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

    // 조각 2(D1)에서 이 앞에 상품 행 잠금이, 저장 뒤에 집계 호출이 붙는다. 순서를 뒤집으면
    // 교착이다 — 근거는 specs/product-rating.md.
    @Transactional
    public void write(ReviewWriteForm form, long memberId) {
        // 폼을 연 뒤 제출까지 시간이 벌어질 수 있고, 폼을 거치지 않은 직접 호출도 막아야 한다.
        OrderReviewTargetView target = requireWritableTarget(form.getOrderItemId(), memberId);

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
