package com.cakeshop.domain.community.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : NoticeStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
// 상수 이름만 있고 붙일 문구가 없어 필드·생성자가 없는 형태
public enum NoticeStatus {
    PUBLISHED,
    DELETED;

    // PUBLISHED -> DELETED 만 가능하고, DELETED 는 어디로도 가지 않는다
    // 매개변수 타입이 NoticeStatus 라서 CommentStatus.DELETED 를 넘기면 컴파일 자체가 안 된다
    public boolean canTransitionTo(NoticeStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PUBLISHED -> next == DELETED;
            case DELETED -> false;
        };
    }
}
