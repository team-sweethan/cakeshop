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

    /**
     * 게시글 한 줄과 회원 도메인에서 받은 작성자를 합쳐 화면용 DTO를 만든다.
     *
     * <p>{@code author}가 null이면 회원 행을 찾지 못한 것이므로 탈퇴로 간주한다. members를
     * INNER JOIN하던 때는 그런 게시글이 목록에서 통째로 사라졌지만, 글은 남기고 표시명만
     * 바꾸는 것이 DOMAIN.md 8절의 규칙이다.</p>
     */
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

    public String authorName() {
        return authorWithdrawn ? WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";
}
