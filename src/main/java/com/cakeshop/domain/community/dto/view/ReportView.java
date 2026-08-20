package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.dto.query.ReportRow;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : ReportView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
// 관리자 게시글 상세에 붙는 신고 한 줄. 신고자 표시명·사유·처리 상태를 담는다.
// 신고자도 탈퇴할 수 있어서 닉네임 대신 reporterName() 을 거쳐 화면에 나간다.
public record ReportView(
        Long id,
        String reporterNickname,
        boolean reporterWithdrawn,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {

    // ReportRow(신고 테이블에서 읽은 한 줄) + 신고자 -> 화면용 한 줄로 합친다.
    // reporter = null 을 탈퇴로 보는 처리는 PostListView.of 와 같다.
    public static ReportView of(ReportRow row, MemberCommunityView reporter) {
        return new ReportView(
                row.id(),
                reporter == null ? null : reporter.nickname(),
                reporter == null || reporter.withdrawn(),
                row.reason(),
                row.status(),
                row.createdAt()
        );
    }

    public String reporterName() {
        return reporterWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : reporterNickname;
    }

    // 아직 처리하지 않은 신고인지. 관리자 화면이 "미처리 n건" 을 셀 때 이 판단을 쓴다.
    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }
}
