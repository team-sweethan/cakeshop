package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.query.AdminNoticeDetailRow;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : AdminNoticeDetailView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
// 관리자 공지 상세·수정 화면 한 건이다. 목록판에 content 와 updatedAt 이 더 붙은 모양이다.
// 여기서도 status·기간·현재 시각을 NoticeDisplayStatus 한 값으로 접어서 싣는다.
public record AdminNoticeDetailView(
        Long id,
        String title,
        String content,
        NoticeDisplayStatus displayStatus,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // now 는 호출한 Service 가 넘긴다. 안에서 LocalDateTime.now()를 부르지 않아 판정 시각이 고정된다.
    public static AdminNoticeDetailView of(AdminNoticeDetailRow row, LocalDateTime now) {
        return new AdminNoticeDetailView(
                row.id(),
                row.title(),
                row.content(),
                NoticeDisplayStatus.of(row.status(), row.startsAt(), row.endsAt(), now),
                row.startsAt(),
                row.endsAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    // 화면의 수정 버튼 노출용. 삭제된 공지만 손댈 수 없다.
    public boolean editable() {
        return displayStatus != NoticeDisplayStatus.DELETED;
    }
}
