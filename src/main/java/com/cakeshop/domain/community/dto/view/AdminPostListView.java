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
// 관리자 게시글 목록 화면(admin/community/list)의 한 줄을 담는 상자다.
// DB에서 읽은 AdminPostListRow 와 회원 도메인이 준 MemberCommunityView 를 of()에서 합쳐 만든다.
// record: 괄호 안에 적은 것이 곧 필드 + 생성자 + id() 같은 접근 메서드가 된다.
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

    // author == null 은 회원 조회에서 그 memberId가 안 온 경우다 -> 닉네임은 null, 탈퇴로 본다.
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

    // 화면이 th:text="${row.authorName}" 로 부르는 파생 메서드다.
    // 탈퇴 회원이면 닉네임 대신 PostListView 가 가진 고정 문구를 내보낸다.
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    public boolean hasPendingReports() {
        return pendingReportCount > 0;
    }
}
