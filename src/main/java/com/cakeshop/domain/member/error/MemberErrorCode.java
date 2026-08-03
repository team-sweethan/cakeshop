package com.cakeshop.domain.member.error;

import com.cakeshop.global.error.ErrorCode;

public enum MemberErrorCode implements ErrorCode {

    DUPLICATE_EMAIL("MEMBER_001", "이미 사용 중인 이메일입니다.", 400),
    NOT_FOUND("MEMBER_002", "회원을 찾을 수 없습니다.", 404),
    INVALID_CURRENT_PASSWORD("MEMBER_003", "현재 비밀번호가 일치하지 않습니다.", 400),
    PASSWORD_MISMATCH("MEMBER_004", "새 비밀번호가 일치하지 않습니다.", 400),
    UPDATE_FAILED("MEMBER_005", "회원 정보 수정에 실패했습니다.", 500),
    WITHDRAW_FAILED("MEMBER_006", "회원 탈퇴 처리에 실패했습니다.", 500),
    INVALID_STATUS_TRANSITION("MEMBER_007", "현재 회원 상태에서는 요청한 상태로 변경할 수 없습니다.", 400),
    INVALID_STATUS_REASON("MEMBER_008", "상태 변경 사유를 확인해 주세요.", 400),
    STATUS_HISTORY_SAVE_FAILED("MEMBER_009", "회원 상태 변경 이력 저장에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;

    MemberErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
