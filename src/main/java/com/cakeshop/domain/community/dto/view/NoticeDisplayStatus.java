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
// DB에 저장된 값이 아니라 화면에서만 쓰는 파생 상태다.
// 테이블에는 NoticeStatus(ACTIVE/DELETED)와 기간 두 칸만 있고,
// "예정 / 노출 중 / 종료 / 삭제됨"은 of()가 현재 시각과 대조해 그때그때 계산한다.
public enum NoticeDisplayStatus {
    SCHEDULED("예정"),
    VISIBLE("노출 중"),
    ENDED("종료"),
    DELETED("삭제됨");

    private final String label;

    NoticeDisplayStatus(String label) {
        this.label = label;
    }

    // 위에서부터 걸리는 것이 답이 되는 계단식 판정이다.
    // 1. 삭제됐으면 기간과 상관없이 DELETED
    // 2. 시작일이 있고 아직 그 앞이면 SCHEDULED
    // 3. 종료일이 있고 이미 그 시각에 닿았으면 ENDED
    // 4. 셋 다 아니면 VISIBLE
    //
    // 경계: 시작은 이상(now == startsAt 이면 노출), 종료는 미만(now == endsAt 이면 종료).
    // !now.isBefore(endsAt) 가 "같거나 뒤"를 뜻해서 종료 시각 정각이 ENDED 로 떨어진다.
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

    // 화면에는 상수 이름(SCHEDULED) 대신 이 한글 label 을 보여 준다.
    public String getLabel() {
        return label;
    }
}
