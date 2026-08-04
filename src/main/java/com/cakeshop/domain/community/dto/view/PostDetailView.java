package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 커뮤니티 게시글 상세 화면에 출력할 정보를 담는다.
 *
 * Mapper는 상태와 무관하게 게시글을 조회하고, 어떤 상태를 누구에게 보여줄지는
 * CommunityService가 판단한다(docs/community/DOMAIN.md 4.3). 그래서 status와
 * blockedReason이 함께 담긴다.
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record PostDetailView(
        Long id,
        Long memberId,          // 작성자. 소유권 판단에 쓴다
        Long categoryId,
        String categoryName,
        String title,
        String content,         // 순수 텍스트. HTML은 허용하지 않는다(DOMAIN.md 7)
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        String blockedReason,   // 관리자가 기록한 차단 사유
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 화면에 표시할 작성자명. 표시 문구는 목록과 같아야 하므로 PostListView의 상수를
     * 함께 쓴다(DOMAIN.md 8).
     */
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /**
     * 작성 후 수정된 적이 있는지. 수정 이력 테이블을 두지 않고 두 시각의 차이로만
     * 판단한다(DOMAIN.md 6.3). 조회수 증가가 updated_at을 건드리면 이 값이 무너지므로,
     * 조회수 UPDATE는 updated_at을 명시적으로 보존한다(CommunityMapper.xml 참고).
     */
    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }

    /** 관리자에게 차단된 게시글인지. */
    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }
}
