package com.cakeshop.domain.order.error;

import com.cakeshop.global.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    INVALID_STATUS_TRANSITION("ORDER_001", "허용되지 않는 주문 상태 변경입니다.", 400),
    MEMBER_NOT_AVAILABLE("ORDER_002", "주문 가능한 로그인 회원이 아닙니다.", 403),
    EMPTY_ORDER_ITEMS("ORDER_003", "주문할 상품이 없습니다.", 400),
    GENERAL_PRODUCT_REQUIRED("ORDER_004", "일반 상품만 함께 주문할 수 있습니다.", 400),
    CUSTOM_PRODUCT_REQUIRED("ORDER_011", "주문 제작 상품만 요청할 수 있습니다.", 400),
    INVALID_QUANTITY("ORDER_005", "상품 수량이 올바르지 않습니다.", 400),
    INVALID_PRODUCT_OPTION("ORDER_006", "상품 옵션 선택이 올바르지 않습니다.", 400),
    ORDER_SAVE_FAILED("ORDER_007", "주문 저장에 실패했습니다.", 500),
    ORDER_AMOUNT_EXCEEDED("ORDER_008", "주문 금액이 허용 범위를 초과했습니다.", 400),
    INVALID_ORDER_AMOUNT("ORDER_009", "주문 금액은 0원보다 커야 합니다.", 400),
    ORDER_AMOUNT_CHANGED("ORDER_010", "상품 가격이 변경되었습니다. 주문 금액을 다시 확인해 주세요.", 409),
    PICKUP_TIME_UNAVAILABLE("ORDER_012", "선택한 픽업 시각까지 제작할 수 없습니다.", 409);

    private final String code;
    private final String message;
    private final int status;

    OrderErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
