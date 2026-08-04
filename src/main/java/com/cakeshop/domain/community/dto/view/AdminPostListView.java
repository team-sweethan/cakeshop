package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 관리자 목록 한 줄. 고객 목록(PostListView)과 갈라 두는 이유가 둘 있다.
 *
 * 하나는 보는 범위가 다르다는 것이다. 관리자는 모든 상태를 보므로 status가 필요하고,
 * 고객 목록에는 PUBLISHED만 나오므로 그 컬럼이 아예 없다(docs/community/DOMAIN.md 4.3).
 *
 * 다른 하나는 정렬 기준이다. 관리자 목록은 "미처리 신고 많은 순"으로 설 수 있어야 해서
 * pendingReportCount를 함께 센다(6.7). 조회수·좋아요는 관리자가 조치를 정하는 데 쓰지
 * 않으므로 담지 않는다.
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
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

    /** 화면에 표시할 작성자명. 고객 화면과 같은 문구를 쓴다(DOMAIN.md 8). */
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 아직 조치하지 않은 신고가 있는지. 목록에서 눈에 띄게 하는 데 쓴다. */
    public boolean hasPendingReports() {
        return pendingReportCount > 0;
    }
}
