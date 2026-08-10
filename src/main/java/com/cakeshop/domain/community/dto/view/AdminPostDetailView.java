package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : AdminPostDetailView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record AdminPostDetailView(
        Long id,
        Long memberId,
        String categoryName,
        String title,
        String content,
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        String blockedReason,
        LocalDateTime blockedAt,
        String blockedByNickname,
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 상세 한 줄과 작성자·차단 관리자를 합쳐 화면용 DTO를 만든다.
     *
     * <p>{@code blockedByAdmin}은 차단 기록이 없으면 null 이고, 그때 닉네임도 null 이 된다.
     * 원래 SQL 이 LEFT JOIN 이었으므로 화면에서 비는 것은 그대로다. 작성자 쪽 규칙은
     * {@link PostListView#of}와 같다.</p>
     */
    public static AdminPostDetailView of(
            AdminPostDetailRow row,
            MemberCommunityView author,
            MemberCommunityView blockedByAdmin
    ) {
        return new AdminPostDetailView(
                row.id(),
                row.memberId(),
                row.categoryName(),
                row.title(),
                row.content(),
                author == null ? null : author.nickname(),
                author == null || author.withdrawn(),
                row.status(),
                row.blockedReason(),
                row.blockedAt(),
                blockedByAdmin == null ? null : blockedByAdmin.nickname(),
                row.viewCount(),
                row.likeCount(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    public boolean hasBlockRecord() {
        return blockedAt != null;
    }
}
