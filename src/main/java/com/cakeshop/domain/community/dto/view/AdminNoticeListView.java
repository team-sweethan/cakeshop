package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.query.AdminNoticeListRow;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : AdminNoticeListView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record AdminNoticeListView(
        Long id,
        String title,
        NoticeDisplayStatus displayStatus,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {

    public static AdminNoticeListView of(AdminNoticeListRow row, LocalDateTime now) {
        return new AdminNoticeListView(
                row.id(),
                row.title(),
                NoticeDisplayStatus.of(row.status(), row.startsAt(), row.endsAt(), now),
                row.startsAt(),
                row.endsAt(),
                row.createdAt()
        );
    }

    public boolean deletable() {
        return displayStatus != NoticeDisplayStatus.DELETED;
    }
}
