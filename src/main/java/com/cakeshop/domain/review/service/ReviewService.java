package com.cakeshop.domain.review.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.service.MemberReviewQueryService;
import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.command.ReviewUpdateCommand;
import com.cakeshop.domain.review.dto.query.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.query.ReviewRow;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.dto.view.ReviewReplyView;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.domain.review.mapper.ReviewReplyMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final int PRODUCT_PREVIEW_SIZE = 3;

    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper reviewReplyMapper;
    private final OrderReviewQueryService orderReviewQueryService;
    private final ProductReviewCommandService productReviewCommandService;
    private final ProductQueryService productQueryService;
    private final MemberReviewQueryService memberReviewQueryService;
    private final ReviewNotificationService reviewNotificationService;

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

    @Transactional(readOnly = true)
    public PageResult<ProductReviewView> getProductReviews(
            long productId, PageRequest pageRequest) {

        requireVisibleProduct(productId);

        long total = reviewMapper.countPublishedByProductId(productId);
        if (total <= pageRequest.getOffset()) {
            return new PageResult<>(List.of(), pageRequest, total);
        }

        List<ProductReviewView> content = toProductReviewViews(
                reviewMapper.findPublishedByProductId(
                        productId, pageRequest.getOffset(), pageRequest.getSize()));

        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public List<ProductReviewView> getProductReviewPreview(long productId) {
        return toProductReviewViews(
                reviewMapper.findPublishedByProductId(productId, 0, PRODUCT_PREVIEW_SIZE));
    }

    @Transactional(readOnly = true)
    public PageResult<MyReviewView> getMyReviews(long memberId, PageRequest pageRequest) {
        long total = reviewMapper.countByMemberId(memberId);
        if (total <= pageRequest.getOffset()) {
            return new PageResult<>(List.of(), pageRequest, total);
        }

        List<ReviewRow> rows = reviewMapper.findByMemberId(
                memberId, pageRequest.getOffset(), pageRequest.getSize());

        Map<Long, OrderReviewSnapshotView> snapshots = findOrderSnapshots(rows);
        Map<Long, ReviewReplyView> replies = findReplies(rows);

        List<MyReviewView> content = rows.stream()
                .map(row -> MyReviewView.from(
                        row, snapshots.get(row.orderItemId()), replies.get(row.id())))
                .toList();

        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public MyReviewView getEditableReview(long reviewId, long memberId) {
        ReviewRow review = requireEditableReview(reviewId, memberId);

        return MyReviewView.from(
                review,
                findOrderSnapshots(List.of(review)).get(review.orderItemId()),
                null);
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

        reviewNotificationService.notifyNewReview(review.getId(), memberId);
    }

    @Transactional
    public void edit(long reviewId, ReviewEditForm form, long memberId) {
        ReviewRow review = requireEditableReview(reviewId, memberId);

        productReviewCommandService.lockForRating(review.productId());

        requireApplied(
                reviewMapper.update(new ReviewUpdateCommand(
                        reviewId,
                        memberId,
                        form.getOverallRating(),
                        form.getTasteRating(),
                        form.getDesignRating(),
                        form.getServiceRating(),
                        form.getContent())),
                reviewId,
                memberId);

        recalculateRating(review.productId());
    }

    @Transactional
    public void delete(long reviewId, long memberId) {
        ReviewRow review = requireEditableReview(reviewId, memberId);

        productReviewCommandService.lockForRating(review.productId());

        requireApplied(reviewMapper.deleteByAuthor(reviewId, memberId), reviewId, memberId);

        recalculateRating(review.productId());
    }

    private ReviewRow requireEditableReview(long reviewId, long memberId) {
        return requireEditable(reviewMapper.findById(reviewId), memberId);
    }

    private ReviewRow requireEditable(ReviewRow review, long memberId) {
        if (review == null
                || !Objects.equals(review.memberId(), memberId)
                || review.status() == ReviewStatus.DELETED) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }

        if (review.status() == ReviewStatus.BLOCKED) {
            throw new BusinessException(ReviewErrorCode.BLOCKED_REVIEW);
        }

        return review;
    }

    private void requireApplied(int affectedRows, long reviewId, long memberId) {
        if (affectedRows > 0) {
            return;
        }

        requireEditable(reviewMapper.findByIdForUpdate(reviewId), memberId);

        throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_TRANSITION);
    }

    // 후기를 바꾼 쓰기와 같은 트랜잭션이어야 한다. 후기만 커밋되고 집계가 실패하면 그 상품에
    // 다음 쓰기가 올 때까지 아무도 모르는 채 틀린 평점과 정렬이 나간다 (D1). MANDATORY 가
    // 그 요구를 관리자 숨김(C4)처럼 바깥에서 부르는 자리에서도 강제한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculateRating(long productId) {
        ProductRatingAggregate aggregate = reviewMapper.aggregateForUpdate(productId);

        productReviewCommandService.applyReviewAggregate(
                productId, aggregate.averageRating(), aggregate.reviewCount());
    }

    private void requireVisibleProduct(long productId) {
        try {
            productQueryService.getSalesInfo(productId);
        } catch (BusinessException e) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
    }

    private List<ProductReviewView> toProductReviewViews(List<ReviewRow> rows) {
        Map<Long, MemberReviewView> authors = findAuthors(rows);
        Map<Long, ReviewReplyView> replies = findReplies(rows);

        return rows.stream()
                .map(row -> ProductReviewView.from(
                        row, authors.get(row.memberId()), replies.get(row.id())))
                .toList();
    }

    private Map<Long, ReviewReplyView> findReplies(List<ReviewRow> rows) {
        List<Long> reviewIds = rows.stream()
                .map(ReviewRow::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (reviewIds.isEmpty()) {
            return Map.of();
        }

        return reviewReplyMapper.findByReviewIds(reviewIds).stream()
                .map(ReviewReplyView::from)
                .collect(Collectors.toMap(ReviewReplyView::reviewId, Function.identity()));
    }

    private Map<Long, MemberReviewView> findAuthors(List<ReviewRow> rows) {
        List<Long> memberIds = rows.stream()
                .map(ReviewRow::memberId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return memberReviewQueryService.getMembersByIds(memberIds).stream()
                .collect(Collectors.toMap(MemberReviewView::id, Function.identity()));
    }

    private Map<Long, OrderReviewSnapshotView> findOrderSnapshots(List<ReviewRow> rows) {
        List<Long> orderItemIds = rows.stream()
                .map(ReviewRow::orderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return orderReviewQueryService.findOrderItemSnapshots(orderItemIds).stream()
                .collect(Collectors.toMap(
                        OrderReviewSnapshotView::orderItemId, Function.identity()));
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
