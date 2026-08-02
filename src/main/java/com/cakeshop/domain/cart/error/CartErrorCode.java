package com.cakeshop.domain.cart.error;

import com.cakeshop.global.error.ErrorCode;

public enum CartErrorCode implements ErrorCode {

    PRODUCT_NOT_ON_SALE("CART_001", "판매 중이 아닌 상품입니다.", 400),
    ITEM_NOT_FOUND("CART_002", "장바구니 상품을 찾을 수 없습니다.", 404),
    INVALID_OPTION("CART_003", "선택할 수 없는 상품 옵션입니다.", 400),
    REQUIRED_OPTION_MISSING("CART_004", "필수 상품 옵션을 선택해 주세요.", 400),
    INVALID_OPTION_SELECTION("CART_005", "상품 옵션 선택 방식이 올바르지 않습니다.", 400),
    OUT_OF_STOCK("CART_006", "상품 재고가 부족합니다.", 400),
    UPDATE_FAILED("CART_007", "장바구니 변경에 실패했습니다.", 409),
    CUSTOM_PRODUCT_NOT_SUPPORTED("CART_008", "주문 제작 상품은 제작 옵션을 먼저 선택해 주세요.", 400);

    private final String code;
    private final String message;
    private final int status;

    CartErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
