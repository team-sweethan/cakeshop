package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.AdminPostDetailRow;
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
// 관리자 게시글 상세 화면 한 건을 담는 상자다. 본문·차단 기록·집계를 한 번에 싣는다.
// AdminPostDetailRow(게시글) + MemberCommunityView 둘(작성자, 차단한 관리자)을 of()에서 합친다.
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

    // null 이 들어올 수 있는 자리가 둘이다.
    // author == null: 회원 조회에 없는 memberId -> 닉네임 null + 탈퇴로 본다.
    // blockedByAdmin == null: 차단된 적이 없는 글 -> blockedByNickname 도 null 로 비워 둔다.
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

    // 아래 넷은 화면의 th:if / th:text 가 그대로 부르는 파생 메서드다.
    // 필드에 없는 값을 화면에서 계산하지 않고 여기서 미리 판정해 둔다.
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    // isBlocked()와 다르다: 차단이 풀린 뒤에도 기록은 남아 있어서 여기는 계속 true 다.
    public boolean hasBlockRecord() {
        return blockedAt != null;
    }
}
