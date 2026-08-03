package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * posts 테이블 한 행을 표현하는 MyBatis POJO다.
 *
 * <p>게시글 쓰기 경로(작성·수정)의 파라미터로만 쓴다. 읽기 경로는 이 클래스를 쓰지 않는다 —
 * 목록·상세는 화면에 필요한 만큼만 담은 {@code dto/view}의 record로 조회한다.
 *
 * <p>그래서 여기에는 조회수·좋아요 수·차단 기록처럼 <b>쓰기 경로가 건드리지 않는 컬럼이
 * 없다.</b> 없는 필드는 잊은 것이 아니라, 있으면 누군가 UPDATE에 끼워 넣을 수 있어서 두지
 * 않은 것이다.
 *
 * <p><b>생성자를 열지 않고 {@link #create}·{@link #edit}로만 만든다.</b> 두 경우는 인자
 * 개수만 다르고 타입이 거의 같아서({@code Long, Long, Long, String, String}) 생성자를
 * 공개하면 <b>순서를 헷갈려도 컴파일된다.</b> 작성인데 수정 자리에 값을 넣으면 남의 글
 * 식별자가 작성자 자리로 들어간다. 이름이 붙어 있으면 그 실수를 부를 자리가 없다.
 *
 * <p><b>{@code id}에만 setter가 있다.</b> 이 객체는 조회 결과로 매핑되지 않으므로 MyBatis가
 * 값을 채울 일이 없고, SQL은 {@code #{title}}처럼 읽기만 한다. 유일한 예외가
 * {@code useGeneratedKeys}로, INSERT 후 생성된 키를 여기에 넣는다. 나머지를 {@code final}로
 * 잠가 두면 <b>만든 뒤에 내용이 바뀐 게시글</b>이라는 상태 자체가 생기지 않는다.
 */
@Getter
public class Post {

    /** 게시글 번호. 작성 시에는 INSERT 후 MyBatis가 채운다(useGeneratedKeys). */
    @Setter
    private Long id;

    private final Long memberId;    // 작성자 회원. 수정·삭제에서는 소유권 조건이 된다
    private final Long categoryId;  // 분류
    private final String title;     // 제목
    private final String content;   // 본문. 순수 텍스트이며 HTML을 허용하지 않는다

    private Post(Long id, Long memberId, Long categoryId, String title, String content) {
        this.id = id;
        this.memberId = memberId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
    }

    /**
     * 새로 저장할 게시글을 만든다.
     *
     * <p>식별자는 아직 없다. INSERT가 끝나야 정해진다.
     *
     * @param memberId 작성자 식별자. 인증 정보에서 얻은 값이어야 한다
     * @param categoryId 분류 식별자
     * @param title 제목
     * @param content 본문
     * @return 저장 대기 상태의 게시글
     */
    public static Post create(Long memberId, Long categoryId, String title, String content) {
        return new Post(null, memberId, categoryId, title, content);
    }

    /**
     * 기존 게시글의 수정 내용을 만든다.
     *
     * @param id 수정할 게시글 식별자
     * @param memberId 요청한 회원 식별자. UPDATE의 소유권 조건으로 쓰인다
     * @param categoryId 분류 식별자
     * @param title 제목
     * @param content 본문
     * @return 수정 대상 게시글
     */
    public static Post edit(
            Long id, Long memberId, Long categoryId, String title, String content) {
        return new Post(id, memberId, categoryId, title, content);
    }
}
