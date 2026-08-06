package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.ReportStatus;

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

    public String reporterName() {
        return reporterWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : reporterNickname;
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }
}
