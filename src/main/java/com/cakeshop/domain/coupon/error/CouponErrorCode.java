package com.cakeshop.domain.coupon.error;

import com.cakeshop.global.error.ErrorCode;

/**
 * 쿠폰 도메인의 업무 규칙 위반과 저장 실패를 표현한다.
 * Controller는 목록 작업의 예상 가능한 오류를 Flash 메시지로 변환한다.
 */
public enum CouponErrorCode implements ErrorCode {

    EXPIRED_COUPON("COUPON_001", "만료된 쿠폰입니다.", 400),
    CREATE_FAILED("COUPON_005", "쿠폰 등록에 실패했습니다.", 500),
    NOT_FOUND("COUPON_006", "쿠폰을 찾을 수 없습니다.", 404),
    UPDATE_FAILED("COUPON_007", "쿠폰 수정에 실패했습니다.", 500),
    QUANTITY_BELOW_ISSUED("COUPON_008", "총 발급 수량은 이미 발급된 수량보다 작을 수 없습니다.", 400),
    NOT_ACTIVE("COUPON_009", "발급 중인 쿠폰만 중지할 수 있습니다.", 400),
    DEACTIVATE_FAILED("COUPON_010", "쿠폰 발급 중지에 실패했습니다.", 500),
    NOT_INACTIVE("COUPON_011", "발급 중지된 쿠폰만 발급을 재개할 수 있습니다.", 400),
    ACTIVATE_FAILED("COUPON_012", "쿠폰 발급 재개에 실패했습니다.", 500),
    // 쿠폰 수정 가능 범위(종료/시작 시각)에 따른 정책 위반 오류다.
    CANNOT_EDIT_ENDED_COUPON("COUPON_013", "종료된 쿠폰은 수정할 수 없습니다.", 400),
    CANNOT_MODIFY_FIELDS("COUPON_014", "수정 불가능한 필드가 변경되었습니다.", 400),
    EXPIRES_AT_EXTENSION_ONLY("COUPON_015", "쿠폰 종료 일시는 기존 값과 같거나 이후여야 합니다.", 400),
    ISSUED_QUANTITY_EXCEEDED("COUPON_016", "총 발급 수량을 초과하여 발급할 수 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CouponErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
