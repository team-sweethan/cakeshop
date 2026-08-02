package com.cakeshop.domain.payment.error;

import com.cakeshop.global.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {

    AMOUNT_MISMATCH("PAYMENT_001", "결제 금액이 일치하지 않습니다.", 400),
    READY_PAYMENT_NOT_FOUND("PAYMENT_002", "결제 가능한 결제 시도를 찾을 수 없습니다.", 409),
    TOSS_ORDER_ID_MISMATCH("PAYMENT_003", "결제 주문번호가 일치하지 않습니다.", 400),
    PAYMENT_EXPIRED("PAYMENT_004", "결제 가능 시간이 지났습니다.", 409),
    TOSS_APPROVAL_FAILED("PAYMENT_005", "결제 승인에 실패했습니다.", 502),
    PAYMENT_COMPLETE_FAILED("PAYMENT_006", "결제 완료 처리에 실패했습니다.", 500),
    PAYMENT_PREPARATION_FAILED("PAYMENT_007", "결제 준비 저장에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;

    PaymentErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
