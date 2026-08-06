package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

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
