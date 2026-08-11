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
}
