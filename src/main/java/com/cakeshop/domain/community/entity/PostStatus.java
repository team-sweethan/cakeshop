package com.cakeshop.domain.community.entity;

/**
 * 게시글 상태의 DB 저장값이다. 노출 여부는 이 값 하나로만 판단하고,
 * blocked_at은 부가 기록일 뿐 노출 판단에 쓰지 않는다(docs/community/DOMAIN.md 4.1).
 */
public enum PostStatus {
    PUBLISHED,
    DELETED,
    BLOCKED;

    /**
     * 전이 규칙은 docs/community/DOMAIN.md 4.2를 따른다. 차단된 글은 신고·조치의 증거이므로
     * 작성자가 삭제해 없앨 수 없고(BLOCKED -> DELETED 금지), DELETED는 종착 상태다.
     */
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
