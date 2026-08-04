package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.ReportStatus;

import java.time.LocalDateTime;

/**
 * 관리자 상세 화면의 신고 내역 한 줄.
 *
 * 관리자 화면에서만 쓴다. 신고자가 누구인지는 고객 화면 어디에도 나가지 않는다 —
 * 신고는 관리자에게 전달된 보고이지 게시글에 붙는 표시가 아니다(docs/community/DOMAIN.md 6.6).
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record ReportView(
        Long id,
        String reporterNickname,
        boolean reporterWithdrawn,
        String reason,          // 순수 텍스트. 화면에서 이스케이프된다(DOMAIN.md 7)
        ReportStatus status,
        LocalDateTime createdAt
) {

    /** 화면에 표시할 신고자명. 탈퇴해도 신고는 남으므로 이름만 가린다(DOMAIN.md 8). */
    public String reporterName() {
        return reporterWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : reporterNickname;
    }

    /** 아직 관리자가 조치하지 않은 신고인지. */
    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }
}
