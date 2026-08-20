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
// 상세 화면(customer/community/detail) 한 건이 쓰는 값 묶음이다.
// 목록(PostListView) 과 달리 본문 content, 상태 status/blockedReason, 수정 시각 updatedAt 까지 들고 간다.
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

    // PostDetailRow(상세 조회 한 줄) + 작성자 -> 화면용 한 건으로 합친다.
    // author = null 을 탈퇴로 보는 처리는 PostListView.of 와 같다.
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

    // 아래 넷은 화면(Thymeleaf) 이 ${post.authorName}, th:if="${post.blocked}" 처럼 그대로 부르는 값이다.
    // 조건식을 템플릿에 흩어 두지 않고 이름을 붙여 여기 모아 둔다.

    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    // "수정됨" 표시용. 작성 시각보다 수정 시각이 뒤면 한 번이라도 고친 글이다.
    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }

    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }

    // 수정·삭제 버튼을 보일지 정한다. viewerId = null(비로그인) 이면 언제나 false.
    // equals 로 비교하는 이유: Long 은 객체라 == 는 값이 아니라 주소를 본다.
    public boolean isAuthoredBy(Long viewerId) {
        return viewerId != null && viewerId.equals(memberId);
    }
}
