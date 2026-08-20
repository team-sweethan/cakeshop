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
// 관리자 공지 목록 화면의 한 줄이다.
// DB에는 status·startsAt·endsAt 이 따로 있지만, 화면은 "지금 어떻게 보이는가" 하나만 필요하다.
// 그래서 of()에서 셋 + 현재 시각을 NoticeDisplayStatus 한 값으로 접어서 싣는다.
public record AdminNoticeListView(
        Long id,
        String title,
        NoticeDisplayStatus displayStatus,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {

    // now 를 매개변수로 받는다. 안에서 LocalDateTime.now()를 부르지 않으므로
    // 목록 한 화면의 모든 줄이 같은 시각으로 판정되고, 테스트에서도 시각을 정해 넣을 수 있다.
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

    // 화면의 삭제 버튼 노출용. 이미 삭제된 공지만 빼고 예정·노출 중·종료는 모두 지울 수 있다.
    public boolean deletable() {
        return displayStatus != NoticeDisplayStatus.DELETED;
    }
}
