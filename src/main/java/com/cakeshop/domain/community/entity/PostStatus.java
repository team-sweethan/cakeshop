package com.cakeshop.domain.community.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : PostStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public enum PostStatus {
    PUBLISHED,
    DELETED,
    BLOCKED;

    public boolean canTransitionTo(PostStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PUBLISHED -> next == DELETED || next == BLOCKED;
            case BLOCKED -> next == PUBLISHED;
            case DELETED -> false;
        };
    }
}
