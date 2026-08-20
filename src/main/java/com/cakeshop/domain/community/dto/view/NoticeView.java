package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.query.NoticeListRow;

// 고객 화면의 공지 한 줄이다. 목록과 메인 화면 공지 영역이 같이 쓴다.
// 필드가 셋뿐인 것에 주의 — 조회수·좋아요 같은 칸이 없다.
// 날짜 칸도 하나(displayedAt)뿐이고, 그 값은 of()가 골라서 채운다.
public record NoticeView(
        Long id,
        String title,
        LocalDateTime displayedAt
) {

    // NoticeListRow 에는 startsAt 과 createdAt 이 둘 다 있다.
    // 노출 시작일이 정해져 있으면 그 값을, 비어 있으면(바로 노출) 등록일을 화면 날짜로 쓴다.
    public static NoticeView of(NoticeListRow row) {
        return new NoticeView(
                row.id(),
                row.title(),
                row.startsAt() == null ? row.createdAt() : row.startsAt()
        );
    }
}
