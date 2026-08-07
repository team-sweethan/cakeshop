package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.ReportStatus;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 관리자 신고 목록 조회 결과
 * 설명 : post_reports 만 읽은 신고 한 줄이다. 신고자는 회원 도메인에서 받아 채운다.
 * ******************************
 *
 * <p>신고자 자리를 두지 않는 이유는 {@link PostListRow}와 같다.</p>
 */
public record ReportRow(
        Long id,
        Long reporterId,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {
}
