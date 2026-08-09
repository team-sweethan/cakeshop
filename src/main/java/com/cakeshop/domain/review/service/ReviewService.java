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

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-09
 * 기능 : 후기 작성
 * 설명 : 주문 상품 정보와 자격은 주문 도메인 계약에서 받는다. 리뷰가 보는 테이블은 reviews 뿐이다.
 * ******************************
 */
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderReviewQueryService orderReviewQueryService;

    public ReviewService(ReviewMapper reviewMapper, OrderReviewQueryService orderReviewQueryService) {
        this.reviewMapper = reviewMapper;
        this.orderReviewQueryService = orderReviewQueryService;
    }

    /** A1 — 작성할 후기 목록. 제외와 페이징을 계약이 함께 처리한다(뒤에서 거르면 건수가 틀어진다). */
    @Transactional(readOnly = true)
    public PageResult<OrderReviewItemView> getWritableOrderItems(long memberId, PageRequest pageRequest) {
        List<Long> reviewed = reviewMapper.findReviewedOrderItemIds(memberId);

        return orderReviewQueryService.findWritableOrderItems(memberId, reviewed, pageRequest);
    }

    /** A2 — 작성 폼이 그릴 대상. 폼을 열 때도 검증 넷을 모두 통과해야 한다. */
    @Transactional(readOnly = true)
    public OrderReviewTargetView getWriteTarget(long orderItemId, long memberId) {
        return requireWritable(orderItemId, memberId);
    }

    /**
     * A3 — 등록. 폼을 거치지 않은 직접 호출이 있으므로 검증을 그대로 다시 한다.
     *
     * <p>상품 행 잠금과 평점 집계는 조각 2(D1)가 이 앞뒤에 붙인다. 순서는 잠금 → INSERT → 집계다.</p>
     */
    @Transactional
    public void write(ReviewWriteForm form, long memberId) {
        OrderReviewTargetView target = requireWritable(form.getOrderItemId(), memberId);

        Review review = Review.create(
                target.orderItemId(),
                target.productId(),
                memberId,
                form.getOverallRating(),
                form.getTasteRating(),
                form.getDesignRating(),
                form.getServiceRating(),
                form.getContent());

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 아래 중복 검증과 INSERT 사이에 들어온 동시 요청은 검증만으로 막히지 않는다.
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }
    }

    /** 검증 1~4. 없음과 남의 것은 같은 404, 픽업 전은 400, 중복은 409다(A2 / DOMAIN.md 2.5). */
    private OrderReviewTargetView requireWritable(long orderItemId, long memberId) {
        OrderReviewTargetView target = orderReviewQueryService.findReviewTarget(orderItemId, memberId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.ORDER_ITEM_NOT_FOUND));

        if (!target.pickedUp()) {
            throw new BusinessException(ReviewErrorCode.NOT_PICKED_UP);
        }

        if (reviewMapper.existsByOrderItemId(orderItemId)) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }

        return target;
    }
}
