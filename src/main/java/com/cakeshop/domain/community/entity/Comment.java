package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * comments 테이블 한 행. 댓글 작성 SQL의 파라미터로만 쓰고, 조회는 dto/view의 record가 받는다.
 * 그래서 상태·작성 시각처럼 쓰기 경로가 건드리지 않는 컬럼은 필드에 없다.
 *
 * 대댓글 식별자가 없다. 컬럼은 V0에 있지만 1차에 대댓글은 없고, 값을 넣는 코드가 없으면
 * 값도 들어갈 수 없다(DOMAIN.md 6.4). 잊은 것이 아니라 두지 않은 것이다.
 *
 * 수정용 생성자도 없다. 댓글에는 수정이 없어서(6.4) 만들 수 있는 것은 새 댓글뿐이다.
 * 생성자를 막고 create로만 만드는 것은 Post와 같은 이유다 — 인자가 Long, Long, String이라
 * 게시글 번호와 작성자 번호를 바꿔 넣어도 컴파일된다.
 */
@Getter
public class Comment {

    // 댓글 번호. INSERT 후 MyBatis가 채운다(useGeneratedKeys).
    // 나중에 정해지는 값이라 여기만 final이 아니다.
    @Setter
    private Long id;

    private final Long postId;
    private final Long memberId;   // 작성자. 삭제에서는 소유권 조건이 된다
    private final String content;  // 순수 텍스트. HTML은 허용하지 않는다

    private Comment(Long id, Long postId, Long memberId, String content) {
        this.id = id;
        this.postId = postId;
        this.memberId = memberId;
        this.content = content;
    }

    /** 새로 저장할 댓글. memberId는 인증 정보에서 얻은 값이어야 한다. id는 INSERT가 끝나야 정해진다. */
    public static Comment create(Long postId, Long memberId, String content) {
        return new Comment(null, postId, memberId, content);
    }
}
