package com.cakeshop.domain.product.error;

import com.cakeshop.global.error.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    NOT_ON_SALE(
            "PRODUCT_001",
            "판매 중인 상품이 아닙니다.",
            400
    ),

    NOT_FOUND(
            "PRODUCT_002",
            "상품을 찾을 수 없습니다.",
            404
    ),

    INVALID_CATEGORY(
            "PRODUCT_003",
            "선택할 수 없는 카테고리입니다.",
            400
    ),

    OPTION_GROUP_NOT_FOUND(
            "PRODUCT_004",
            "옵션 그룹을 찾을 수 없습니다.",
            404
    ),

    OPTION_NOT_FOUND(
            "PRODUCT_005",
            "상품 옵션을 찾을 수 없습니다.",
            404
    ),

    INVALID_OPTION_GROUP(
            "PRODUCT_006",
            "옵션 그룹 입력값을 확인해 주세요.",
            400
    ),

    INVALID_OPTION(
            "PRODUCT_007",
            "상품 옵션 입력값을 확인해 주세요.",
            400
    ),

    INVALID_PRODUCT_POLICY(
            "PRODUCT_008",
            "상품 유형에 맞는 준비 일수를 입력해 주세요.",
            400
    );

    private final String code;
    private final String message;
    private final int status;

    ProductErrorCode(
            String code,
            String message,
            int status
    ) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}
