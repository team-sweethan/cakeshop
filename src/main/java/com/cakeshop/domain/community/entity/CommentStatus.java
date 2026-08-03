package com.cakeshop.domain.community.entity;

/**
 * 댓글 상태의 DB 저장값이다. 삭제된 댓글은 목록에서 지우지 않고 "삭제된 댓글입니다"
 * 자리 표시로 남기되, 댓글 개수 집계에서는 제외한다(docs/community/DOMAIN.md 4.4).
 */
public enum CommentStatus {
    PUBLISHED,
    DELETED;

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
