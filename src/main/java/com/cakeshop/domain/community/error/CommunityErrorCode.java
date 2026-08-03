package com.cakeshop.domain.community.error;

import com.cakeshop.global.error.ErrorCode;

public enum CommunityErrorCode implements ErrorCode {

    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403),

    /**
     * 게시글이 없거나, 요청자에게 보여줄 수 없는 상태다. 삭제·차단·미존재를 구분하지 않고
     * 하나의 코드로 응답한다. 403은 "그 자리에 글이 존재한다"는 사실을 흘리기 때문이다
     * (docs/community/DOMAIN.md 4.3).
     */
    POST_NOT_FOUND("COMMUNITY_002", "게시글을 찾을 수 없습니다.", 404),

    /**
     * 선택지에 없는 카테고리로 글을 저장하려 했다. 화면에는 활성 카테고리만 나오므로
     * (DOMAIN.md 6.8) 정상 흐름에서는 나오지 않는다. 요청을 직접 만들었거나, 글을 쓰는
     * 동안 카테고리가 비활성으로 바뀐 경우다.
     */
    CATEGORY_NOT_FOUND("COMMUNITY_003", "선택할 수 없는 분류입니다.", 400),

    /**
     * 댓글이 없거나, 남의 댓글이거나, 이미 지워졌거나, 다른 게시글의 댓글이다. 게시글과
     * 같은 이유로 넷을 구분하지 않는다 — 구분하면 그 자리에 댓글이 있다는 사실이 드러난다
     * (DOMAIN.md 4.3).
     */
    COMMENT_NOT_FOUND("COMMUNITY_004", "댓글을 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;

    CommunityErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
