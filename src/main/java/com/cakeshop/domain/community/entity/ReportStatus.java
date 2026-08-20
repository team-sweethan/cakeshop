package com.cakeshop.domain.community.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : ReportStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
// 괄호 안 문구는 생성자로 넘어가 label 에 담긴다
// ReportStatus.PENDING.getLabel() -> "미처리"
public enum ReportStatus {
    PENDING("미처리"),
    RESOLVED("처리 완료"),
    REJECTED("기각됨");

    private final String label;

    // enum 생성자는 자동으로 private 이라 상수 선언부에서만 호출된다
    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // PENDING -> RESOLVED, PENDING -> REJECTED
    // RESOLVED, REJECTED -> 어디로도 못 감 (항상 false)
    public boolean canTransitionTo(ReportStatus next) {
        if (next == null) {
            return false;
        }
        // case 에 값 여러 개를 쉼표로 묶으면 결과가 같은 갈래를 한 줄로 적을 수 있다
        return switch (this) {
            case PENDING -> next == RESOLVED || next == REJECTED;
            case RESOLVED, REJECTED -> false;
        };
    }
}
