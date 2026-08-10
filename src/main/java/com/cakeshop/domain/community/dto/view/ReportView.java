package com.cakeshop.domain.community.dto.view;

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
public record ReportView(
        Long id,
        String reporterNickname,
        boolean reporterWithdrawn,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {

    /** 신고 한 줄과 신고자를 합쳐 화면용 DTO를 만든다. 근거는 {@link PostListView#of}. */
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

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }
}
