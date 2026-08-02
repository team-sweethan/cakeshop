package com.cakeshop.domain.community.error;

import com.cakeshop.global.error.ErrorCode;

public enum CommunityErrorCode implements ErrorCode {

    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403),

    /**
     * 게시글이 없거나, 요청자에게 보여줄 수 없는 상태다.
     *
     * <p>삭제·차단·미존재를 구분하지 않고 하나의 코드로 응답한다. 403은 "그 자리에 글이
     * 존재한다"는 사실을 흘리기 때문이다(docs/community/DOMAIN.md 4.3).
     */
    POST_NOT_FOUND("COMMUNITY_002", "게시글을 찾을 수 없습니다.", 404);

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
