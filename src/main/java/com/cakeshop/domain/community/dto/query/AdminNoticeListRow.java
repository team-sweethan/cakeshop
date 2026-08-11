package com.cakeshop.domain.community.dto.query;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.NoticeStatus;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 관리자 공지 목록 한 줄의 조회 결과를 전달한다.
 * ******************************
 */
public record AdminNoticeListRow(
        Long id,
        String title,
        NoticeStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
}
