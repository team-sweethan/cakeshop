package com.cakeshop.domain.statistics.error;

import com.cakeshop.global.error.ErrorCode;

public enum StatisticsErrorCode implements ErrorCode {

    INVALID_DATE_RANGE("STATISTICS_001", "조회 기간이 올바르지 않습니다.", 400),
    STATISTICS_NOT_READY("STATISTICS_002", "선택한 기간의 통계 집계가 완료되지 않았습니다.", 409),
    PRODUCT_STATISTICS_NOT_READY(
            "STATISTICS_003",
            "선택한 기간의 상품별 통계 집계가 완료되지 않았습니다.",
            409
    );

    private final String code;
    private final String message;
    private final int status;

    StatisticsErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
