package com.cakeshop.domain.community.error;

import com.cakeshop.global.error.ErrorCode;

public enum CommunityErrorCode implements ErrorCode {

    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403);

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
