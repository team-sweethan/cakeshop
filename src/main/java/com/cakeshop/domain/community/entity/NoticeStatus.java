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
public enum NoticeStatus {
    PUBLISHED,
    DELETED;

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
