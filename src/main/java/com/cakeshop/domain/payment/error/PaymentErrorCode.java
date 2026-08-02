package com.cakeshop.domain.payment.error;

import com.cakeshop.global.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {

    AMOUNT_MISMATCH("PAYMENT_001", "결제 금액이 일치하지 않습니다.", 400),
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
