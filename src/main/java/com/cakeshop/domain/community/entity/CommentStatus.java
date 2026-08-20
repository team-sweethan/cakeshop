package com.cakeshop.domain.community.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : CommentStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
// 값이 둘뿐이고 붙일 문구가 없어서 PostStatus 와 달리 필드도 생성자도 없다
// 괄호 없이 상수 이름만 적으면 인자 없는 기본 생성자가 쓰인다
public enum CommentStatus {
    PUBLISHED,
    DELETED;

    // PUBLISHED -> DELETED 한 방향만 열려 있다
    // DELETED -> 어디로도 못 감 (항상 false)
    public boolean canTransitionTo(CommentStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PUBLISHED -> next == DELETED;
            case DELETED -> false;
        };
    }
}
