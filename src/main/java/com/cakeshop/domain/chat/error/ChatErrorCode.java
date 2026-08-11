package com.cakeshop.domain.chat.error;

import com.cakeshop.global.error.ErrorCode;

public enum ChatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND("CHAT_001", "채팅방을 찾을 수 없습니다.", 404),
    INVALID_IMAGE_FILE("CHAT_002", "유효하지 않거나 지원하지 않는 이미지 파일 형식입니다.", 400),
    IMAGE_TOO_LARGE("CHAT_003", "채팅 이미지 파일 크기는 5MB 이하이어야 합니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ChatErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
