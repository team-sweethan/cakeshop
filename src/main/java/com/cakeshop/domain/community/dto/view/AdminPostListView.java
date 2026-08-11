package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.AdminPostListRow;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : AdminPostListView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record AdminPostListView(
        Long id,
        String categoryName,
        String title,
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        long pendingReportCount,
        LocalDateTime createdAt
) {

    /** 목록 한 줄과 작성자를 합쳐 화면용 DTO를 만든다. 근거는 {@link PostListView#of}. */
    public static AdminPostListView of(AdminPostListRow row, MemberCommunityView author) {
        return new AdminPostListView(
                row.id(),
                row.categoryName(),
                row.title(),
                author == null ? null : author.nickname(),
                author == null || author.withdrawn(),
                row.status(),
                row.pendingReportCount(),
                row.createdAt()
        );
    }

    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    public boolean hasPendingReports() {
        return pendingReportCount > 0;
    }
}
