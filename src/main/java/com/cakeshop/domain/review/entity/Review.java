package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Review {

    @Setter
    private Long id;

    private final Long orderItemId;
    private final Long productId;
    private final Long memberId;
    private final Integer overallRating;
    private final Integer tasteRating;
    private final Integer designRating;
    private final Integer serviceRating;
    private final String content;

    private Review(
            Long id,
            Long orderItemId,
            Long productId,
            Long memberId,
            Integer overallRating,
            Integer tasteRating,
            Integer designRating,
            Integer serviceRating,
            String content) {
        this.id = id;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.memberId = memberId;
        this.overallRating = overallRating;
        this.tasteRating = tasteRating;
        this.designRating = designRating;
        this.serviceRating = serviceRating;
        this.content = content;
    }

    // productId 는 요청값이 아니라 order_items 에서 파생시킨 것을 넘긴다. 위치 인자라 팩터리가
    // 막아 주지 못하므로 파생은 호출부인 Service 의 책임이다 (R4).
    public static Review create(
            Long orderItemId,
            Long productId,
            Long memberId,
            Integer overallRating,
            Integer tasteRating,
            Integer designRating,
            Integer serviceRating,
            String content) {
        return new Review(
                null, orderItemId, productId, memberId,
                overallRating, tasteRating, designRating, serviceRating, content);
    }

    public static Review edit(
            Long id,
            Long memberId,
            Integer overallRating,
            Integer tasteRating,
            Integer designRating,
            Integer serviceRating,
            String content) {
        return new Review(
                id, null, null, memberId,
                overallRating, tasteRating, designRating, serviceRating, content);
    }
}
