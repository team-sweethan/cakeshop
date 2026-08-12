package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.NoticeStatus;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 관리자 화면에 보여 줄 공지의 노출 상태를 판정한다.
 * ******************************
 */
public enum NoticeDisplayStatus {
    SCHEDULED("예정"),
    VISIBLE("노출 중"),
    ENDED("종료"),
    DELETED("삭제됨");

    private final String label;

    NoticeDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 저장된 상태와 노출 기간을 합쳐 지금 어떻게 보이는지를 판정한다.
     *
     * <p>경계는 고객 조회의 {@code visibleNotice}와 같은 규칙이다 — 시작은 이상, 종료는
     * 미만이다. 두 곳이 갈리면 <b>목록에 `노출 중`인데 고객 화면에는 없는</b> 공지가 생기고,
     * 그 어긋남은 화면만 봐서는 드러나지 않는다.</p>
     */
    public static NoticeDisplayStatus of(
            NoticeStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime now) {

        if (status == NoticeStatus.DELETED) {
            return DELETED;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return SCHEDULED;
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return ENDED;
        }
        return VISIBLE;
    }
}
