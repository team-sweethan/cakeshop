package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : CommentView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record CommentView(
        Long id,
        Long postId,
        Long memberId,
        String authorNickname,
        boolean authorWithdrawn,
        String content,
        CommentStatus status,
        LocalDateTime createdAt
) {

    public String authorName() {
        return authorNameOf(authorNickname, authorWithdrawn);
    }

    /**
     * 회원 한 명의 표시명을 고른다. 알림 문구처럼 {@code CommentView} 를 만들지 않는 자리가 쓴다.
     *
     * <p>회원 행을 못 찾은 경우({@code null})도 탈퇴와 같이 다룬다 — {@link #of} 가 채우는
     * {@code authorWithdrawn} 이 그 규칙이고, 여기서 갈리면 같은 사람이 화면과 알림에서 다른
     * 이름으로 나온다.</p>
     */
    public static String authorNameOf(MemberCommunityView author) {
        return author == null
                ? authorNameOf(null, true)
                : authorNameOf(author.nickname(), author.withdrawn());
    }

    private static String authorNameOf(String nickname, boolean withdrawn) {
        return withdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : nickname;
    }

    /** 댓글 한 줄과 작성자를 합쳐 화면용 DTO를 만든다. 근거는 {@link PostListView#of}. */
    public static CommentView of(CommentRow row, MemberCommunityView author) {
        return new CommentView(
                row.id(),
                row.postId(),
                row.memberId(),
                author == null ? null : author.nickname(),
                author == null || author.withdrawn(),
                row.content(),
                row.status(),
                row.createdAt()
        );
    }

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }
}
