package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.PostListRow;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : PostListView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
// record 는 불변 데이터 상자다. 아래 괄호 안에 적은 것(컴포넌트)만으로 필드·생성자·getter 가
// 자동으로 만들어지고, getTitle() 이 아니라 이름 그대로 title() 이 값을 꺼내는 메서드가 된다.
// 이 패키지의 View 는 전부 같은 문법이라 이 설명은 여기 한 번만 적는다.

// 목록 화면(customer/community/list) 의 한 줄에 그려질 값 묶음이다.
// PageResult<PostListView> 의 T 자리에 들어가 List<PostListView> 로 실려 나간다.
public record PostListView(
        Long id,
        String categoryName,
        String title,
        String authorNickname,
        boolean authorWithdrawn,
        long viewCount,
        long likeCount,
        long commentCount,
        LocalDateTime createdAt
) {

    // 탈퇴·미확인 작성자를 대신할 표시명. 같은 패키지의 다른 View 도 이 한 자리를 가져다 쓴다
    // (public 이 아니라 package-private 이라 community.dto.view 밖에서는 안 보인다).
    static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";

    // PostListRow(게시글 테이블에서 읽은 한 줄) + MemberCommunityView(회원 도메인이 건네준 작성자)
    // -> PostListView(화면용 한 줄) 로 합치는 정적 팩토리다.
    // author = null 은 회원 행을 못 찾았다는 뜻이라 authorWithdrawn = true 로 채워 둔다.
    public static PostListView of(PostListRow row, MemberCommunityView author) {
        return new PostListView(
                row.id(),
                row.categoryName(),
                row.title(),
                author == null ? null : author.nickname(),
                author == null || author.withdrawn(),
                row.viewCount(),
                row.likeCount(),
                row.commentCount(),
                row.createdAt()
        );
    }

    // 화면이 실제로 부르는 이름. 닉네임을 그대로 쓰지 않고 탈퇴 여부를 한 번 거른다.
    public String authorName() {
        return authorWithdrawn ? WITHDRAWN_AUTHOR_NAME : authorNickname;
    }
}
