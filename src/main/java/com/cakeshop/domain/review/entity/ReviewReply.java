package com.cakeshop.domain.review.entity;

import lombok.Getter;
import lombok.Setter;

// review_replies 테이블 한 행. 후기 하나에 사장님 답글 하나가 붙는다
@Getter
public class ReviewReply {

    // id 만 값을 갈아 끼울 수 있다. INSERT 후 DB 가 매긴 번호를 MyBatis 가 setId 로 채워 준다
    @Setter
    private Long id;

    private final Long reviewId;
    private final Long adminId;
    private final String content;

    // Review 와 같은 모양: private 생성자 + 아래 정적 팩토리 하나로 만드는 통로를 좁혔다
    private ReviewReply(Long id, Long reviewId, Long adminId, String content) {
        this.id = id;
        this.reviewId = reviewId;
        this.adminId = adminId;
        this.content = content;
    }

    // id = null 로 새 객체를 만든다 (저장 전이라 번호가 없다)
    // adminId 는 요청 파라미터가 아니라 로그인한 관리자에서 꺼내 넣는다
    public static ReviewReply create(Long reviewId, Long adminId, String content) {
        return new ReviewReply(null, reviewId, adminId, content);
    }

}
