package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.query.NoticeListRow;

/**
 * 고객 화면의 공지 한 줄(docs/community/specs/community-notice.md B8·B9).
 *
 * <p><b>날짜가 하나뿐인 것이 요점이다.</b> 화면이 보여 주는 날짜는 정렬 키와 같은 값이어야
 * 한다 — 등록일을 보여 주면서 노출 시작일로 정렬하면 목록이 뒤죽박죽으로 보이고, 그 화면은
 * 정렬이 깨진 것과 구분되지 않는다.
 *
 * <p>조회수도 반응도 싣지 않는다. 공지에는 그런 것이 없다.
 */
public record NoticeView(
        Long id,
        String title,
        LocalDateTime displayedAt
) {

    public static NoticeView of(NoticeListRow row) {
        return new NoticeView(
                row.id(),
                row.title(),
                row.startsAt() == null ? row.createdAt() : row.startsAt()
        );
    }
}
