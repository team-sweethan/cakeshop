package com.cakeshop.domain.review.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 도메인 모델
 * 설명 : ReviewStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public enum ReviewStatus {
    PUBLISHED,
    DELETED,
    BLOCKED;

    public boolean canTransitionTo(ReviewStatus next) {
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
