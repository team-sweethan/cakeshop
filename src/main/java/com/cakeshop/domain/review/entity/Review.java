package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

// reviews 테이블 한 행에 해당하는 객체. INSERT 할 값을 모아 Mapper 에 넘기는 데 쓴다
// @Getter: 필드마다 getOrderItemId(), getOverallRating() 같은 읽기 메서드를 롬복이 컴파일 때 만들어 넣는다
@Getter
public class Review {

    // id 에만 @Setter 를 따로 붙였다 -> setId(Long) 하나만 생긴다
    // INSERT 후 DB 가 매긴 번호를 MyBatis 가 이 setId 로 도로 채워 준다 (XML 의 useGeneratedKeys)
    // 그래서 id 만 final 이 아니다
    @Setter
    private Long id;

    // final 필드는 생성자에서 한 번 채우면 끝이다. 뒤에서 값을 갈아 끼울 통로 자체가 없다
    private final Long orderItemId;
    private final Long productId;
    private final Long memberId;
    private final Integer overallRating;
    private final Integer tasteRating;
    private final Integer designRating;
    private final Integer serviceRating;
    private final String content;

    // 생성자가 private 이라 밖에서 new Review(...) 를 쓸 수 없다
    // 만드는 통로를 아래 create(...) 하나로 좁힌 것
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

    // 정적 팩토리: new 대신 Review.create(...) 로 만든다
    // id 자리에 null 을 넣는다 — 아직 저장 전이라 DB 가 번호를 주지 않았다
    // 인자가 이름 없이 순서로만 붙는 위치 인자라, Long 끼리 자리를 바꿔 넣어도 컴파일은 통과한다
    //     create(orderItemId, productId, memberId, ...) 순서를 호출부에서 지켜야 한다
    // productId 자리에는 화면에서 받은 값이 아니라 order_items 에서 읽어 낸 값을 넣는다
    //     타입이 둘 다 Long 이라 잘못 넣어도 컴파일러가 못 잡는다
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
