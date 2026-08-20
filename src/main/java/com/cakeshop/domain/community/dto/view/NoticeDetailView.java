package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.query.NoticeDetailRow;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : NoticeDetailView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
// 고객 공지 상세 화면 한 건이다. NoticeView 에 content 가 하나 더 붙은 모양이다.
// 날짜 칸을 고르는 방식도 NoticeView.of 와 같다(시작일 우선, 없으면 등록일).
public record NoticeDetailView(
        Long id,
        String title,
        String content,
        LocalDateTime displayedAt
) {

    public static NoticeDetailView of(NoticeDetailRow row) {
        return new NoticeDetailView(
                row.id(),
                row.title(),
                row.content(),
                row.startsAt() == null ? row.createdAt() : row.startsAt()
        );
    }
}
