package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.view.ProductRatingSummary;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.domain.product.mapper.ProductReviewMapper;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 평점 집계 계약
 * 설명 : 후기가 바뀔 때 products 의 평균 평점과 후기 수를 다시 계산한다. 조각 2(#33).
 * ******************************
 *
 * <p>{@code ProductQueryService} 가 아니라 별도 Service 인 이유는 그쪽이 읽기 전용 계약이고
 * 집계는 {@code products} 에 쓰기 때문이다. 쓰기 계약의 형판은 {@link ProductStockService} 다.
 *
 * <p>두 매퍼를 함께 주입받는다 — 잠금은 기존 {@link ProductMapper}, 집계와 갱신은
 * {@link ProductReviewMapper}. 같은 패키지 안이라 도메인 경계를 넘지 않는다.
 *
 * <p><b>호출자는 후기 쓰기와 같은 트랜잭션에서 불러야 한다.</b> 후기가 먼저 커밋되고 집계가
 * 실패하면 {@code average_rating}·{@code review_count} 가 실제 후기와 영구히 어긋난다. 그
 * 상품에 다음 쓰기가 올 때까지 아무도 모르는 채 잘못된 평점과 정렬이 나간다.
 */
@Service
public class ProductRatingService {

    private final ProductMapper productMapper;
    private final ProductReviewMapper productReviewMapper;

    public ProductRatingService(
            ProductMapper productMapper,
            ProductReviewMapper productReviewMapper
    ) {
        this.productMapper = productMapper;
        this.productReviewMapper = productReviewMapper;
    }

    /**
     * 집계 전에 상품 행을 배타 잠금한다.
     *
     * <p><b>후기 INSERT 보다 먼저 불러야 한다.</b> 저장한 뒤에 잠그면 이미 늦다 —
     * {@code reviews} INSERT 가 FK 확인으로 {@code products} 행에 공유 잠금을 먼저 걸고,
     * 집계가 그것을 배타 잠금으로 승격하려 한다. 같은 상품에 후기 두 건이 동시에 들어오면
     * 서로의 공유 잠금을 기다리며 교착이다. <b>리뷰끼리만이 아니다</b> — 누가 그 상품을
     * 주문하는 동안 다른 사람이 후기를 쓰면 재고 차감 흐름과도 교착한다.
     *
     * <p>먼저 배타 잠금을 잡으면 승격이 없어 교착도 없다. 주문 흐름의
     * {@code ProductStockService.decreaseStock} 이 이미 같은 순서를 쓴다.
     *
     * @param productId 잠글 상품 식별자
     * @throws BusinessException 상품이 없는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForRating(long productId) {
        if (productMapper.findSalesInfoByIdForUpdate(productId) == null) {
            throw new BusinessException(ProductErrorCode.NOT_FOUND);
        }
    }

    /**
     * 그 상품의 공개 후기로 평균 평점과 후기 수를 다시 계산해 반영한다.
     *
     * <p>증분이 아니라 재계산이다. 후기는 좋아요보다 훨씬 적게 쌓여 증분의 이점이 없고,
     * 호출 지점이 등록·수정·삭제·숨김·숨김 해제 다섯 곳이라 증분은 "여기서도 조정해야 하나"를
     * 매번 판단해야 한다. 평균의 증분 갱신은 특히 어긋나기 쉽다.
     *
     * <p>{@link #lockForRating} 을 먼저 부른 뒤에 호출한다.
     *
     * @param productId 집계할 상품 식별자
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(long productId) {
        ProductRatingSummary summary =
                productReviewMapper.summarizeForUpdate(productId);

        productReviewMapper.updateRating(
                productId,
                summary.averageRating(),
                summary.reviewCount()
        );
    }
}
