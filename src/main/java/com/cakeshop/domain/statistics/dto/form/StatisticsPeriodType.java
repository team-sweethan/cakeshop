package com.cakeshop.domain.statistics.dto.form;

import lombok.Getter;

/** 관리자 기간별 통계의 조회 유형. */
@Getter
public enum StatisticsPeriodType {

    /** 시작일과 종료일을 직접 선택하는 기간 조회. */
    RANGE("기간"),

    /** 월요일부터 일요일까지 조회하는 주간 조회. */
    WEEKLY("주간"),

    /** 선택한 달의 1일부터 말일까지 조회하는 월간 조회. */
    MONTHLY("월간");

    private final String displayName;

    StatisticsPeriodType(String displayName) {
        this.displayName = displayName;
    }
}
