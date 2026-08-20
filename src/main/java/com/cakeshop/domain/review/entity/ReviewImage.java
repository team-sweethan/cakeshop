package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

// review_images 테이블 한 행. 후기 한 건에 여러 장이 달리고 sortOrder 가 화면에 뿌릴 순서다
@Getter
public class ReviewImage {

    // id 만 값을 갈아 끼울 수 있다. INSERT 후 DB 가 매긴 번호를 MyBatis 가 setId 로 채워 준다
    @Setter
    private Long id;

    private final Long reviewId;
    private final String imageUrl;

    // sortOrder 만 int 다 -> null 이 될 수 없고 0 이 첫 장이다 (Integer 였다면 null 이 가능하다)
    private final int sortOrder;

    // Review 와 같은 모양: private 생성자 + 아래 정적 팩토리 하나로 만드는 통로를 좁혔다
    private ReviewImage(Long id, Long reviewId, String imageUrl, int sortOrder) {
        this.id = id;
        this.reviewId = reviewId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    // id = null 로 새 객체를 만든다 (저장 전이라 번호가 없다)
    // 예: create(37L, "/upload/review/a1b2.jpg", 0)
    public static ReviewImage create(Long reviewId, String imageUrl, int sortOrder) {
        return new ReviewImage(null, reviewId, imageUrl, sortOrder);
    }

}
