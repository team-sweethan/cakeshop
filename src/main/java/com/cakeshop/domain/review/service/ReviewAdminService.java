package com.cakeshop.domain.review.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.member.dto.view.MemberReviewView;
import com.cakeshop.domain.member.service.MemberReviewQueryService;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.dto.view.AdminReviewDetailView;
import com.cakeshop.domain.review.dto.view.AdminReviewFilter;
import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.dto.view.AdminReviewRating;
import com.cakeshop.domain.review.dto.view.ReviewReplyView;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.ReviewReply;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewAdminMapper;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.domain.review.mapper.ReviewReplyMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

@Service
public class ReviewAdminService {

    private final ReviewAdminMapper reviewAdminMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper reviewReplyMapper;
    private final ReviewService reviewService;
    private final ReviewNotificationService reviewNotificationService;
    private final ProductReviewCommandService productReviewCommandService;
    private final MemberReviewQueryService memberReviewQueryService;
    private final OrderReviewQueryService orderReviewQueryService;

    public ReviewAdminService(
            ReviewAdminMapper reviewAdminMapper,
            ReviewMapper reviewMapper,
            ReviewReplyMapper reviewReplyMapper,
            ReviewService reviewService,
            ReviewNotificationService reviewNotificationService,
            ProductReviewCommandService productReviewCommandService,
            MemberReviewQueryService memberReviewQueryService,
            OrderReviewQueryService orderReviewQueryService) {
        this.reviewAdminMapper = reviewAdminMapper;
        this.reviewMapper = reviewMapper;
        this.reviewReplyMapper = reviewReplyMapper;
        this.reviewService = reviewService;
        this.reviewNotificationService = reviewNotificationService;
        this.productReviewCommandService = productReviewCommandService;
        this.memberReviewQueryService = memberReviewQueryService;
        this.orderReviewQueryService = orderReviewQueryService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminReviewListView> getReviews(
            String writer, String product, AdminReviewRating rating, ReviewStatus status,
            PageRequest pageRequest) {

        AdminReviewFilter filter = new AdminReviewFilter(
                searchMemberIds(writer), searchOrderItemIds(product), rating, status);

        long total = reviewAdminMapper.countForAdmin(filter);
        if (total <= pageRequest.getOffset()) {
            return new PageResult<>(List.of(), pageRequest, total);
        }

        List<ReviewRow> rows = reviewAdminMapper.findForAdmin(
                filter, pageRequest.getOffset(), pageRequest.getSize());

        Map<Long, MemberReviewView> authors = findAuthors(rows);
        Map<Long, OrderReviewSnapshotView> snapshots = findOrderSnapshots(rows);

        List<AdminReviewListView> content = rows.stream()
                .map(row -> AdminReviewListView.of(
                        row, authors.get(row.memberId()), snapshots.get(row.orderItemId())))
                .toList();

        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public AdminReviewDetailView getReviewDetail(long reviewId) {
        ReviewRow review = requireFound(reviewMapper.findById(reviewId));

        List<ReviewRow> rows = List.of(review);

        return AdminReviewDetailView.of(
                review,
                findAuthors(rows).get(review.memberId()),
                findOrderSnapshots(rows).get(review.orderItemId()),
                reviewReplyMapper.findByReviewId(reviewId));
    }

    @Transactional
    public void reply(long reviewId, ReviewReplyForm form, long adminId) {
        ReviewReply reply = ReviewReply.create(reviewId, adminId, form.getContent());

        int inserted;

        try {
            inserted = reviewReplyMapper.insertForPublishedReview(reply);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REPLIED);
        }

        if (inserted == 0) {
            requirePublished(reviewId);

            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_TRANSITION);
        }

        // 저장이 성공했으니 후기는 존재하고 PUBLISHED 다. 작성자만 다시 읽어 받는 사람을 정한다.
        reviewNotificationService.notifyReviewReply(
                reviewId, reply.getId(), requireFound(reviewMapper.findById(reviewId)).memberId(),
                adminId);
    }

    @Transactional
    public void editReply(long reviewId, ReviewReplyForm form) {
        if (reviewReplyMapper.updateContentForPublishedReview(reviewId, form.getContent()) > 0) {
            return;
        }

        requirePublished(reviewId);

        throw new BusinessException(ReviewErrorCode.REPLY_NOT_FOUND);
    }

    // 0행의 원인을 최신 행으로 다시 읽는다. 조건이 저장 문장 안에 있어 잠금을 기다리는 동안
    // 커밋된 숨김·삭제는 검증 시점의 스냅샷에 없다 (조각 4·5와 같은 자리).
    private void requirePublished(long reviewId) {
        ReviewRow review = reviewMapper.findByIdForUpdate(reviewId);

        if (review == null || review.status() == ReviewStatus.DELETED) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }

        if (review.status() == ReviewStatus.BLOCKED) {
            throw new BusinessException(ReviewErrorCode.BLOCKED_REVIEW);
        }
    }

    @Transactional
    public void block(long reviewId) {
        changeStatus(reviewId, ReviewStatus.PUBLISHED, ReviewStatus.BLOCKED);
    }

    @Transactional
    public void unblock(long reviewId) {
        changeStatus(reviewId, ReviewStatus.BLOCKED, ReviewStatus.PUBLISHED);
    }

    // 조각 4 의 삭제와 잠금 순서를 맞춘다. 상품을 먼저 잡지 않으면 같은 상품에 숨김과 삭제가
    // 동시에 오갈 때 두 트랜잭션이 서로 반대 순서로 잠가 교착한다.
    private void changeStatus(long reviewId, ReviewStatus expected, ReviewStatus next) {
        ReviewRow review = requireFound(reviewMapper.findById(reviewId));

        if (!review.status().canTransitionTo(next)) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_TRANSITION);
        }

        productReviewCommandService.lockForRating(review.productId());

        if (reviewAdminMapper.updateStatus(reviewId, expected, next) == 0) {
            // 잠금을 기다리는 동안 커밋된 조치는 검증 시점의 스냅샷에 없다. 최신 행을 다시 읽어야
            // 원인이 드러난다.
            requireFound(reviewMapper.findByIdForUpdate(reviewId));

            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_TRANSITION);
        }

        reviewService.recalculateRating(review.productId());
    }

    private ReviewRow requireFound(ReviewRow review) {
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }

        return review;
    }

    // 조건을 걸지 않는 것과 "일치하는 것이 없다"를 가른다. 빈 검색어면 계약을 부르지 않고
    // null 을 돌려주고, 검색어가 있으면 결과가 비어도 그대로 넘겨 0건이 되게 한다.
    private List<Long> searchMemberIds(String writer) {
        if (isBlank(writer)) {
            return null;
        }

        return memberReviewQueryService.findMemberIdsByNickname(writer);
    }

    private List<Long> searchOrderItemIds(String product) {
        if (isBlank(product)) {
            return null;
        }

        return orderReviewQueryService.findOrderItemIdsByProductName(product);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
}
