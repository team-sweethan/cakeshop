package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * posts 테이블 한 행. 작성·수정 SQL의 파라미터로만 쓰고, 조회는 dto/view의 record가 받는다.
 * 그래서 조회수·좋아요 수·차단 기록처럼 쓰기 경로가 건드리지 않는 컬럼은 필드에 없다.
 *
 * 생성자를 막고 create·edit로만 만든다. 인자 타입이 Long, Long, Long, String, String으로
 * 거의 같아서, 생성자를 열면 순서를 바꿔 넣어도 컴파일된다.
 */
@Getter
public class Post {

    // 게시글 번호. 작성 시에는 INSERT 후 MyBatis가 채운다(useGeneratedKeys).
    // 나중에 정해지는 값이라 여기만 final이 아니다.
    @Setter
    private Long id;

    private final Long memberId;    // 작성자. 수정·삭제에서는 소유권 조건이 된다
    private final Long categoryId;
    private final String title;
    private final String content;   // 순수 텍스트. HTML은 허용하지 않는다

    private Post(Long id, Long memberId, Long categoryId, String title, String content) {
        this.id = id;
        this.memberId = memberId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
    }

    /** 새로 저장할 게시글. memberId는 인증 정보에서 얻은 값이어야 한다. id는 INSERT가 끝나야 정해진다. */
    public static Post create(Long memberId, Long categoryId, String title, String content) {
        return new Post(null, memberId, categoryId, title, content);
    }

    /** 수정할 게시글. memberId는 UPDATE의 소유권 조건이므로 인증 정보에서 얻은 값이어야 한다. */
    public static Post edit(
            Long id, Long memberId, Long categoryId, String title, String content) {
        return new Post(id, memberId, categoryId, title, content);
    }
}
