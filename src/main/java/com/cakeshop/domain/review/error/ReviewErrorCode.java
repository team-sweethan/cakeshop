package com.cakeshop.domain.review.error;

import com.cakeshop.global.error.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {

    NOT_PICKED_UP("REVIEW_001", "픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400),
    ORDER_ITEM_NOT_FOUND("REVIEW_003", "주문 상품을 찾을 수 없습니다.", 404),
    ALREADY_REVIEWED("REVIEW_004", "이미 후기를 작성한 주문 상품입니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    ReviewErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
