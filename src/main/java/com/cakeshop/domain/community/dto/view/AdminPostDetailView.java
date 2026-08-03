package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 관리자 상세 화면의 게시글. <b>차단된 글의 본문을 볼 수 있는 유일한 화면</b>이 여기다 —
 * 고객 경로에서는 관리자도 404를 받는다(docs/community/DOMAIN.md 4.3).
 *
 * 고객 상세(PostDetailView)와 갈라 두는 것은 담는 값이 다르기 때문이다. 관리자에게는
 * 차단 기록 일체(누가·언제·왜)가 필요하고, 고객 화면에는 사유 하나만 나간다. 반대로
 * categoryId처럼 수정 폼을 채우는 값은 여기 없다 — 관리자는 글을 고치지 않는다(6.7).
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record AdminPostDetailView(
        Long id,
        Long memberId,
        String categoryName,
        String title,
        String content,             // 순수 텍스트. HTML은 허용하지 않는다(DOMAIN.md 7)
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        String blockedReason,
        LocalDateTime blockedAt,
        String blockedByNickname,   // 차단한 관리자. 차단된 적이 없으면 null이다
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 화면에 표시할 작성자명. 고객 화면과 같은 문구를 쓴다(DOMAIN.md 8). */
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 지금 차단되어 있는지. 모더레이션 패널의 버튼을 가르는 데 쓴다. */
    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }

    /** 작성자가 지운 글인지. 지워진 글은 차단도 해제도 할 수 없다(DOMAIN.md 4.2). */
    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    /**
     * 과거에 차단된 적이 있는지. 해제해도 blocked_* 는 되돌리지 않으므로(DOMAIN.md 4.2)
     * 지금 상태와 무관하게 기록이 남는다. 이 값이 참인데 isBlocked()가 거짓이면
     * "차단됐다가 풀린 글"이다.
     */
    public boolean hasBlockRecord() {
        return blockedAt != null;
    }
}
