package com.cakeshop.domain.review.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cakeshop.domain.review.dto.view.ProductReviewView;

/**
 * 상품 도메인에 제공하는 후기 조회 계약.
 *
 * <p>상품 상세가 후기 미리보기를 모델에 담기 위해 쓴다. 상품 쪽이 {@link ReviewService} 나
 * {@code reviews} 테이블을 직접 보지 않도록 필요한 조회 하나만 연다(DOMAIN 2.7,
 * {@code specs/review-read.md} B1).</p>
 *
 * <p>붙이는 방식은 2026-08-06 에 시은님과 합의한 것이다 — 미리보기는 개수를 고정해 모델로
 * 주입하고, 전체 목록은 리뷰가 소유한 별도 화면이 맡는다.</p>
 */
@Service
public class ReviewProductQueryService {

    private final ReviewService reviewService;

    public ReviewProductQueryService(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 상품 상세에 붙일 최신 후기 {@value ReviewService#PRODUCT_PREVIEW_SIZE} 건.
     *
     * <p>페이징하지 않는다. 페이징 있는 목록을 상세에 통째로 실으면 2 페이지부터 갈 곳이 없어,
     * 개수를 묶어 그 문제를 없앤 것이 이 계약의 전제다.</p>
     *
     * <p>공개 후기가 없으면 빈 목록이다. 그때 전체 목록 링크를 감출지는 화면 정책이라 상세가
     * 정한다.</p>
     */
    public List<ProductReviewView> getPreview(long productId) {
        return reviewService.getProductReviewPreview(productId);
    }
}
