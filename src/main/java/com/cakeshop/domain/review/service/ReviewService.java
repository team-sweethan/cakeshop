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
import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.service.OrderReviewQueryService;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.product.service.ProductReviewCommandService;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductRatingAggregate;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

@Service
public class ReviewService {

    // 상품 상세에 붙는 미리보기 개수. 고정이라 상세에 모델로 주입할 수 있고, 페이징이 전체 목록
    // 화면 한 곳에만 남는다 (B1).
    public static final int PRODUCT_PREVIEW_SIZE = 3;

    private final ReviewMapper reviewMapper;
    private final OrderReviewQueryService orderReviewQueryService;
    private final ProductReviewCommandService productReviewCommandService;
    private final ProductQueryService productQueryService;
    private final MemberReviewQueryService memberReviewQueryService;

    public ReviewService(
            ReviewMapper reviewMapper,
            OrderReviewQueryService orderReviewQueryService,
            ProductReviewCommandService productReviewCommandService,
            ProductQueryService productQueryService,
            MemberReviewQueryService memberReviewQueryService) {
        this.reviewMapper = reviewMapper;
        this.orderReviewQueryService = orderReviewQueryService;
        this.productReviewCommandService = productReviewCommandService;
        this.productQueryService = productQueryService;
        this.memberReviewQueryService = memberReviewQueryService;
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

    /**
     * 상품 상세에 붙일 최신 후기 {@value #PRODUCT_PREVIEW_SIZE} 건.
     *
     * <p>여기서는 상품 공개 여부를 다시 보지 않는다. 상품 상세가 이미 판매 중이 아닌 상품을
     * 404 로 끝내고, 이 조회는 그 뒤에만 불린다.</p>
     */
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

        // 인증 회원으로 좁혀 읽은 행이라 주문 스냅샷을 받을 자격은 여기서 이미 판정됐다.
        List<ReviewRow> rows = reviewMapper.findByMemberId(
                memberId, pageRequest.getOffset(), pageRequest.getSize());

        Map<Long, OrderReviewSnapshotView> snapshots = findOrderSnapshots(rows);

        List<MyReviewView> content = rows.stream()
                .map(row -> MyReviewView.of(row, snapshots.get(row.orderItemId())))
                .toList();

        return new PageResult<>(content, pageRequest, total);
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

    /**
     * 고객에게 공개되는 상품인지 먼저 확인한다.
     *
     * <p>없는 상품과 판매 중지 상품을 <b>같은 404 로 합친다.</b> 계약이 던지는 대로 흘리면
     * 없는 상품은 404, 판매 중지 상품은 400 이라 주소를 훑어 "있지만 내린 상품"을 골라낼 수
     * 있다. 화면 안에서는 상세가 먼저 404 라 드러나지 않고 주소를 직접 넣는 경로에서만
     * 보인다(B1).</p>
     */
    private void requireVisibleProduct(long productId) {
        try {
            productQueryService.getSalesInfo(productId);
        } catch (BusinessException e) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
    }

    private List<ProductReviewView> toProductReviewViews(List<ReviewRow> rows) {
        Map<Long, MemberReviewView> authors = findAuthors(rows);

        return rows.stream()
                .map(row -> ProductReviewView.of(row, authors.get(row.memberId())))
                .toList();
    }

    // 후기마다 회원을 따로 조회하면 N+1 이다 (DOMAIN 2.7). 없는 회원은 Map 에서 빠지고 그 자리는
    // View 가 탈퇴로 처리한다.
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
