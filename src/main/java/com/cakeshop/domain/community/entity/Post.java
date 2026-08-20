package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

// @Getter: 롬복이 컴파일 시점에 필드마다 getter 를 만들어 붙인다
// 소스에는 안 보이지만 .class 안에는 들어 있다
// getId(), getMemberId(), getCategoryId(), getTitle(), getContent()
@Getter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : Post 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public class Post {

    // @Setter 는 이 필드 하나에만 붙었다 -> setId() 만 생기고 setTitle() 같은 건 없다
    // 아래 필드들과 달리 final 이 아니라서 만든 뒤에도 값을 바꿀 수 있다
    @Setter
    private Long id;

    // final 필드: 생성자에서 한 번 넣으면 그 뒤로는 바꿀 수 없다
    private final Long memberId;
    private final Long categoryId;
    private final String title;
    private final String content;

    // 생성자가 private: 클래스 바깥에서는 new Post(...) 를 아예 쓸 수 없다
    // 밖에서 Post 를 만드는 길은 바로 아래 create() 하나뿐이 된다
    private Post(Long id, Long memberId, Long categoryId, String title, String content) {
        this.id = id;
        this.memberId = memberId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
    }

    // 정적 팩토리: new 대신 Post.create(37L, 2L, "제목", "본문") 처럼 부른다
    // 매개변수에 id 가 없고 안에서 null 을 대신 넣어주므로,
    // 호출하는 쪽이 id 를 임의의 값으로 채워 넣은 Post 를 만들 방법이 없다
    public static Post create(Long memberId, Long categoryId, String title, String content) {
        return new Post(null, memberId, categoryId, title, content);
    }

}
