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

    /**
     * 후기 작성용.
     *
     * <p>{@code productId}는 요청값이 아니라 {@code order_items.product_id}에서 파생시킨 것을
     * 넘긴다. 같은 값에 이르는 경로가 둘이라 요청값을 그대로 믿으면 남의 상품에 후기를 붙일 수
     * 있다(docs/review/PLAN.md R4). 팩터리는 위치 인자라 이 실수를 막아 주지 않으므로, 파생은
     * 호출부인 Service의 책임이다.
     *
     * <p>{@code status}는 필드로 두지 않는다. 작성 시점의 값은 언제나 {@code PUBLISHED}이고
     * 그 뒤의 변경은 상태 전이({@link ReviewStatus})로만 일어난다.
     */
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
}
