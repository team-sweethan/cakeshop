package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : PostDetailView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record PostDetailView(
        Long id,
        Long memberId,
        Long categoryId,
        String categoryName,
        String title,
        String content,
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        String blockedReason,
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 상세 한 줄과 작성자를 합쳐 화면용 DTO를 만든다. 근거는 {@link PostListView#of}. */
    public static PostDetailView of(PostDetailRow row, MemberCommunityView author) {
        return new PostDetailView(
                row.id(),
                row.memberId(),
                row.categoryId(),
                row.categoryName(),
                row.title(),
                row.content(),
                author == null ? null : author.nickname(),
                author == null || author.withdrawn(),
                row.status(),
                row.blockedReason(),
                row.viewCount(),
                row.likeCount(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }

    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }

    /**
     * 이 글을 쓴 사람인지 본다. 비로그인({@code null})은 언제나 아니다.
     *
     * <p>화면이 수정·신고 버튼을 가릴 때 쓴다. 같은 판단을 Service 쪽에서는
     * {@code CommunityPostAccessPolicy.isAuthor}가 하는데, 그쪽은 매퍼가 읽어 온 행의 원시 ID를
     * 다루고 이 record 는 화면에 나가는 값을 다룬다. <b>두 계층이 각자의 표현으로 같은 규칙을
     * 갖되, 양쪽 다 이름이 붙은 자리에서만 판단한다</b> — 조건식을 직접 쓰면 한쪽이 바뀔 때
     * 버튼과 실제 권한이 어긋나고, 사용자는 눌러 봐야 거절을 안다.
     */
    public boolean isAuthoredBy(Long viewerId) {
        return viewerId != null && viewerId.equals(memberId);
    }
}
