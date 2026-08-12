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
    STATUS_HISTORY_SAVE_FAILED("MEMBER_009", "회원 상태 변경 이력 저장에 실패했습니다.", 500),
    INVALID_EMAIL("MEMBER_010", "이메일 형식을 확인해 주세요.", 400),
    EMAIL_VERIFICATION_RESEND_TOO_SOON(
            "MEMBER_011", "인증번호는 60초 후 다시 요청할 수 있습니다.", 429),
    EMAIL_VERIFICATION_RATE_LIMITED(
            "MEMBER_012", "인증번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.", 429),
    EMAIL_VERIFICATION_SEND_FAILED(
            "MEMBER_013", "인증메일을 발송하지 못했습니다. 잠시 후 다시 시도해 주세요.", 503),
    EMAIL_VERIFICATION_INVALID(
            "MEMBER_014", "인증번호가 올바르지 않거나 만료되었습니다.", 400),
    EMAIL_VERIFICATION_REQUIRED(
            "MEMBER_015", "이메일 인증을 완료해 주세요.", 400),
    EMAIL_VERIFICATION_SAVE_FAILED(
            "MEMBER_016", "이메일 인증 처리에 실패했습니다.", 500);

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
